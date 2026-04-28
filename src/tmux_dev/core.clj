(ns tmux-dev.core
  (:require [babashka.process :as p]
            [clojure.string :as str]))

(defn- session-exists? [session-name]
  (-> (p/process ["tmux" "has-session" "-t" session-name]
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

(defn start
  "Start a tmux session with named windows.

  Config map:
    :session  - tmux session name (required)
    :windows  - vector of [name command] pairs (required)
    :dir      - working directory (default: current dir)
    :print    - vector of strings to print after start (optional)

  Example:
    (start {:session \"my-dev\"
            :windows [[\"frontend\" \"bb frontend\"]
                      [\"backend\"  \"bb backend\"]]
            :print [\"App: http://localhost:8000\"
                    \"API: http://localhost:3000\"]})"
  [{:keys [session windows dir print] :as _config}]
  (assert session ":session is required")
  (assert (seq windows) ":windows is required and must be non-empty")
  (let [dir (or dir (System/getProperty "user.dir"))]
    (when (session-exists? session)
      (println (str "[" session "] session already running. Use restart to recreate."))
      (System/exit 0))
    ;; Create session with first window
    (let [[first-name first-cmd] (first windows)]
      (sh "tmux" "new-session" "-d" "-s" session "-n" first-name "-c" dir)
      (sh "tmux" "send-keys" "-t" (str session ":" first-name) first-cmd "Enter"))
    ;; Create remaining windows
    (doseq [[window-name cmd] (rest windows)]
      (sh "tmux" "new-window" "-t" session "-n" window-name "-c" dir)
      (sh "tmux" "send-keys" "-t" (str session ":" window-name) cmd "Enter"))
    (println (str "[" session "] tmux session started"))
    (when (seq print)
      (doseq [line print]
        (println (str "  " line))))
    (println (str "  Attach: tmux attach -t " session))))

(defn stop
  "Kill the tmux session."
  [{:keys [session]}]
  (assert session ":session is required")
  (if (session-exists? session)
    (do (sh-quiet "tmux" "kill-session" "-t" session)
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
    (sh "tmux" "attach" "-t" session)
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
         (let [result @(p/process ["tmux" "capture-pane" "-t" (str session ":" window-name)
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
    (let [result @(p/process ["tmux" "list-windows" "-t" session "-F" "#{window_name}: #{pane_current_command}"]
                             {:out :string :err :string})]
      (println (str "[" session "] running"))
      (println (str/trim (:out result))))))

(defn make-tasks
  "Generate bb.edn task entries from a config.

  Returns a map of task symbols to task definitions:
    dev         - start the session
    dev:stop    - stop the session
    dev:restart - restart the session
    dev:attach  - attach to the session
    dev:logs    - show logs from all windows
    dev:status  - show session status

  Options:
    :prefix - task name prefix (default: \"dev\")"
  ([config] (make-tasks config {}))
  ([config {:keys [prefix] :or {prefix "dev"}}]
   (let [sym (fn [suffix] (symbol (if suffix (str prefix ":" suffix) prefix)))]
     {(sym nil)        {:doc "Start dev tmux session"
                        :task `(do (require '[tmux-dev.core :as td])
                                   (td/start ~config))}
      (sym "stop")     {:doc "Stop dev tmux session"
                        :task `(do (require '[tmux-dev.core :as td])
                                   (td/stop ~config))}
      (sym "restart")  {:doc "Restart dev tmux session"
                        :task `(do (require '[tmux-dev.core :as td])
                                   (td/restart ~config))}
      (sym "attach")   {:doc "Attach to dev tmux session"
                        :task `(do (require '[tmux-dev.core :as td])
                                   (td/attach ~config))}
      (sym "logs")     {:doc "Show logs from dev tmux session"
                        :task `(do (require '[tmux-dev.core :as td])
                                   (td/logs ~config))}
      (sym "status")   {:doc "Show dev session status"
                        :task `(do (require '[tmux-dev.core :as td])
                                   (td/status ~config))}})))
