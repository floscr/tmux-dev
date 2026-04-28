(ns tmux-dev.env)

(defn string
  "Read env var as string, with fallback."
  [name default]
  (or (System/getenv name) default))

(defn number
  "Read env var as long, with fallback."
  [name default]
  (or (some-> (System/getenv name) parse-long) default))

(defn bool
  "Read env var as boolean. Truthy: \"1\", \"true\", \"yes\"."
  [name default]
  (if-let [v (System/getenv name)]
    (contains? #{"1" "true" "yes"} v)
    default))
