(ns tmux-dev.core
  (:require [babashka.process :as p]
            [clojure.string :as str]
            [clojure.java.io :as io]))

;; tmux prefix-matches a bare `-t name` (`-t app` hits `app-demo` once `app`
;; is gone); `=` makes every session target an exact match.
(defn- exact [session]
  (str "=" session))

(defn- window-target [session window-name]
  (str (exact session) ":" window-name))

(defn- session-exists? [session-name]
  (-> (p/process ["tmux" "has-session" "-t" (exact session-name)]
                 {:err :string :out :string})
      deref
      :exit
      zero?))

(defn- sh [& args]
  (-> (p/process (vec args) {:inherit true})
      deref))

(defn- sh-quiet [& args]
  (-> (p/process (vec args) {:err :string :out :string})
      deref))

(defn parse-env-file
  "Parse a .env file into a map of {\"KEY\" \"VALUE\"}.  
   Skips blank lines and comments (#). Returns nil if file doesn't exist.
   
   Example:
     (parse-env-file \".env\")  ;=> {\"API_KEY\" \"abc123\" \"PORT\" \"8080\"}"
  [path]
  (let [f (io/file path)]
    (when (.exists f)
      (into {}
        (for [line (str/split-lines (slurp f))
              :let [line (str/trim line)]
              :when (and (seq line)
                        (not (str/starts-with? line "#"))
                        (str/includes? line "="))
              :let [[k v] (str/split line #"=" 2)
                    k (str/trim k)
                    v (str/trim (or v ""))]
              :when (and (seq k) (seq v))]
          [k v])))))

(defn start
  "Start a tmux session with named windows.

  Config map:
    :session  - tmux session name (required)
    :windows  - vector of [name command] pairs (required)
    :dir      - working directory (default: current dir)
    :env      - map of environment variables to set in the session (optional)
    :env-file - path to a .env file to load (optional, merged under :env)
    :print    - vector of strings to print after start (optional)

  Example:
    (start {:session \"my-dev\"
            :windows [[\"frontend\" \"bb frontend\"]
                      [\"backend\"  \"bb backend\"]]
            :env-file \".env\"
            :env {\"EXTRA\" \"val\"}
            :print [\"App: http://localhost:8000\"
                    \"API: http://localhost:3000\"]})"
  [{:keys [session windows dir env env-file print] :as _config}]
  (assert session ":session is required")
  (assert (seq windows) ":windows is required and must be non-empty")
  (let [dir (or dir (System/getProperty "user.dir"))
        env-vars (merge (when env-file (parse-env-file env-file)) env)]
    (when (session-exists? session)
      (println (str "[" session "] session already running. Use restart to recreate."))
      (System/exit 0))
    ;; Create session with first window
    (let [[first-name first-cmd] (first windows)
          target (fn [wname] (window-target session wname))
          send-env! (fn [wname]
                      ;; Export each var individually to avoid long-line wrapping
                      (doseq [[k v] env-vars]
                        (sh "tmux" "send-keys" "-t" (target wname)
                            (str "export " k "=" (pr-str v)) "Enter")))]
      (sh "tmux" "new-session" "-d" "-s" session "-n" first-name "-c" dir)
      ;; Also set on the session so future panes/windows inherit them
      (doseq [[k v] env-vars]
        (sh-quiet "tmux" "set-environment" "-t" (exact session) k v))
      (send-env! first-name)
      (sh "tmux" "send-keys" "-t" (target first-name) first-cmd "Enter"))
    ;; Create remaining windows
    (let [target (fn [wname] (window-target session wname))
          send-env! (fn [wname]
                      (doseq [[k v] env-vars]
                        (sh "tmux" "send-keys" "-t" (target wname)
                            (str "export " k "=" (pr-str v)) "Enter")))]
      (doseq [[window-name cmd] (rest windows)]
        (sh "tmux" "new-window" "-t" (str (exact session) ":") "-n" window-name "-c" dir)
        (send-env! window-name)
        (sh "tmux" "send-keys" "-t" (target window-name) cmd "Enter")))
    (println (str "[" session "] tmux session started"))
    (when (seq print)
      (doseq [line print]
        (println (str "  " line))))
    ;; The command sits alone on its line so a copied line runs as-is.
    (println "  Attach with:")
    (println (str "tmux attach -t " session))))

(defn- pane-pids [session]
  (->> (sh-quiet "tmux" "list-panes" "-s" "-t" (exact session) "-F" "#{pane_pid}")
       :out
       str/split-lines
       (remove str/blank?)))

;; The pane shell leads its process session (pid = sid) and, if interactive,
;; ignores TERM, so only the processes it started count.
(defn- session-children-alive? [sid]
  (->> (sh-quiet "pgrep" "-s" sid) :out str/split-lines
       (some #(and (not (str/blank? %)) (not= sid (str/trim %))))))

;; kill-session only SIGHUPs each pane's shell; children that ignore it
;; (npm → node → java chains) reparent to init and keep their ports. Every
;; pane is its own process session, so signal the whole session instead:
;; TERM, up to 10s for graceful shutdown, then KILL.
(defn- kill-pane-processes! [session]
  (let [sids (pane-pids session)]
    (doseq [sid sids] (sh-quiet "pkill" "-TERM" "-s" sid))
    (loop [n 0]
      (when (and (< n 40) (some session-children-alive? sids))
        (Thread/sleep 250)
        (recur (inc n))))
    (doseq [sid sids] (sh-quiet "pkill" "-KILL" "-s" sid))))

(defn stop
  "Kill the tmux session and every process started in its panes."
  [{:keys [session]}]
  (assert session ":session is required")
  (if (session-exists? session)
    (do (kill-pane-processes! session)
        (sh-quiet "tmux" "kill-session" "-t" (exact session))
        (println (str "[" session "] stopped")))
    (println (str "[" session "] not running"))))

(defn restart
  "Kill and restart the tmux session."
  [config]
  (when (session-exists? (:session config))
    (stop config))
  (start config))

(defn attach
  "Attach to the tmux session."
  [{:keys [session]}]
  (assert session ":session is required")
  (if (session-exists? session)
    (sh "tmux" "attach" "-t" (exact session))
    (println (str "[" session "] not running"))))

(defn logs
  "Show recent output from a window (or all windows).

  Options (second arg map):
    :window  - specific window name (default: all windows)
    :lines   - number of lines to capture (default: 50)"
  ([config] (logs config {}))
  ([{:keys [session windows]} {:keys [window lines] :or {lines 50}}]
   (assert session ":session is required")
   (if-not (session-exists? session)
     (println (str "[" session "] not running"))
     (let [target-windows (if window
                            (filter #(= (first %) window) windows)
                            windows)]
       (doseq [[window-name _] target-windows]
         (println (str "── " window-name " ──"))
         (let [result @(p/process ["tmux" "capture-pane" "-t" (window-target session window-name)
                                   "-p" "-S" (str "-" lines)]
                                  {:out :string :err :string})]
           (println (str/trim (:out result))))
         (println))))))

(defn status
  "Print whether the session is running and list its windows."
  [{:keys [session]}]
  (assert session ":session is required")
  (if-not (session-exists? session)
    (println (str "[" session "] not running"))
    (let [result @(p/process ["tmux" "list-windows" "-t" (exact session) "-F" "#{window_name}: #{pane_current_command}"]
                             {:out :string :err :string})]
      (println (str "[" session "] running"))
      (println (str/trim (:out result))))))


