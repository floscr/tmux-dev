# tmux-dev

Babashka library for managing tmux dev sessions. Define windows and commands in a config map, get start/stop/restart/attach/logs/status for free.

## Usage

Add to your `bb.edn`:

```clojure
{:deps {tmux-dev/tmux-dev {:local/root "../tmux-dev"}}
 :tasks
 {dev         {:task (do (require '[tmux-dev.core :as td])
                         (td/start {:session "my-app"
                                    :windows [["frontend" "bb frontend"]
                                              ["backend"  "bb backend"]]
                                    :print   ["App: http://localhost:8000"
                                              "API: http://localhost:3000"]}))}
  dev:stop    {:task (do (require '[tmux-dev.core :as td])
                         (td/stop {:session "my-app"}))}
  dev:restart {:task (do (require '[tmux-dev.core :as td])
                         (td/restart {:session "my-app"
                                      :windows [["frontend" "bb frontend"]
                                                ["backend"  "bb backend"]]}))}
  dev:attach  {:task (do (require '[tmux-dev.core :as td])
                         (td/attach {:session "my-app"}))}
  dev:logs    {:task (do (require '[tmux-dev.core :as td])
                         (td/logs {:session "my-app"
                                   :windows [["frontend" "bb frontend"]
                                             ["backend"  "bb backend"]]}))}
  dev:status  {:task (do (require '[tmux-dev.core :as td])
                         (td/status {:session "my-app"}))}}}
```

Or define the config once:

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

## Config

| Key        | Required | Description                               |
|------------|----------|-------------------------------------------|
| `:session` | yes      | tmux session name                         |
| `:windows` | yes      | vector of `[name command]` pairs          |
| `:dir`     | no       | working directory (default: cwd)          |
| `:print`   | no       | lines to print after session starts       |

## API

| Function  | Description                                  |
|-----------|----------------------------------------------|
| `start`   | Create session with windows. No-op if exists |
| `stop`    | Kill the session                             |
| `restart` | Kill + start                                 |
| `attach`  | Attach to the session (interactive)          |
| `logs`    | Capture recent pane output from all windows  |
| `status`  | Show running state and window list           |

`logs` accepts an optional second map with `:window` (name) and `:lines` (default 50).
