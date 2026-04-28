# tmux-dev

Babashka library for managing tmux dev sessions. Define windows and commands in a config map, get start/stop/restart/attach/logs/status for free.

## Install

Add as a dependency in `bb.edn`:

```clojure
;; local
{:deps {tmux-dev/tmux-dev {:local/root "../tmux-dev"}}}

;; git
{:deps {tmux-dev/tmux-dev {:git/url "https://github.com/floscr/tmux-dev"
                            :git/sha "..."}}}
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
  Attach: tmux attach -t my-app

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
| `stop`      | Kill the session                               |
| `restart`   | Kill + start                                   |
| `attach`    | Attach to the session (interactive)            |
| `logs`      | Capture recent pane output from all windows    |
| `status`    | Show running state and window list             |

`logs` takes an optional second map:

```clojure
(td/logs config {:window "frontend"})  ; single window
(td/logs config {:lines 100})          ; more history
```
