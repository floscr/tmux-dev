# tmux-dev

Babashka library for managing tmux dev sessions. Define windows and commands in a config map, get start/stop/restart/attach/logs/status for free.

## Install

Add as a dependency in `bb.edn`:

```clojure
;; local
{:deps {tmux-dev/tmux-dev {:local/root "../tmux-dev"}}}

;; git
{:deps {tmux-dev/tmux-dev {:git/url "https://github.com/floscr/tmux-dev"
                            :git/sha "6ef67ed56540a60b6862c656b6098139e1e6e82f"}}}
```

## Usage

Define a config map with `:session` and `:windows`, then wire up tasks:

```clojure
{:deps {tmux-dev/tmux-dev {:local/root "../tmux-dev"}}
 :tasks
 {:requires ([tmux-dev.core :as td])
  :init (def config {:session "my-app"
                     :windows [["frontend" "bb frontend"]
                               ["backend"  "bb backend"]]
                     :print   ["App: http://localhost:8000"
                               "API: http://localhost:3000"]})

  dev         {:task (td/start config)}
  dev:stop    {:task (td/stop config)}
  dev:restart {:task (td/restart config)}
  dev:attach  {:task (td/attach config)}
  dev:logs    {:task (td/logs config)}
  dev:status  {:task (td/status config)}}}
```

```
$ bb dev
[my-app] tmux session started
  App: http://localhost:8000
  API: http://localhost:3000
  Attach with:
tmux attach -t my-app

$ bb dev:status
[my-app] running
frontend: node
backend: bb

$ bb dev:logs
── frontend ──
vite v5.4.6 dev server running at http://localhost:8000

── backend ──
[main] INFO server - Started on port 3000

$ bb dev:stop
[my-app] stopped
```

## Config

| Key        | Required | Description                          |
|------------|----------|--------------------------------------|
| `:session` | yes      | tmux session name                    |
| `:windows` | yes      | vector of `[name command]` pairs     |
| `:dir`     | no       | working directory (default: cwd)     |
| `:print`   | no       | lines to print after session starts  |

## API

All functions take the config map as first argument.

| Function    | Description                                    |
|-------------|------------------------------------------------|
| `start`     | Create session with windows. No-op if running  |
| `stop`      | Kill the session and every process its panes started (TERM, then KILL after 10s) |
| `restart`   | Kill + start                                   |
| `attach`    | Attach to the session (interactive)            |
| `logs`      | Capture recent pane output from all windows    |
| `status`    | Show running state and window list             |

Session names are matched exactly: stopping `app` never touches `app-demo`.

`logs` takes an optional second map:

```clojure
(td/logs config {:window "frontend"})  ; single window
(td/logs config {:lines 100})          ; more history
```

## Environment variables

`tmux-dev.env` provides helpers for reading env vars with typed fallbacks. Use these to make ports and other config overridable — this is the foundation for orchestration (e.g. auto port assignment when running multiple projects).

```clojure
(require '[tmux-dev.env :as env])

(env/number "PORT_BACKEND"  3000)   ; => 3000 (or env value as long)
(env/number "PORT_FRONTEND" 8000)   ; => 8000
(env/string "HOST"          "localhost")
(env/bool   "DEBUG"         false)  ; truthy: "1", "true", "yes"
```

Use in your config so ports propagate from the environment:

```clojure
{:tasks
 {:requires ([tmux-dev.core :as td] [tmux-dev.env :as env])
  :init (def port-fe (env/number "PORT_FRONTEND" 8000))
  :init (def port-be (env/number "PORT_BACKEND" 3000))
  :init (def config {:session "my-app"
                     :windows [["backend"  (str "PORT_BACKEND=" port-be " bb backend")]
                               ["frontend" (str "PORT_FRONTEND=" port-fe " bb frontend")]]
                     :print   [(str "App: http://localhost:" port-fe)
                               (str "API: http://localhost:" port-be)]})

  dev         {:task (td/start config)}
  dev:stop    {:task (td/stop config)}
  dev:restart {:task (td/restart config)}}}
```

Then override from the shell:

```
$ PORT_FRONTEND=9000 PORT_BACKEND=4000 bb dev
[my-app] tmux session started
  App: http://localhost:9000
  API: http://localhost:4000
```

## License

MIT, see [LICENSE](LICENSE).
