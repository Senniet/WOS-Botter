# SurvivalOps Agent Bridge (experimental)

An incident-triggered bridge between **existing** SurvivalOps LAN diagnostics and an AI agent. No additional MSI is needed: the webserver already receives diagnostic events. This does **not** send commands to the currently open ChatGPT chat.

## Behavior

- Monitors `~/survivalops-diagnostics/inbox/YYYY-MM-DD/*.json` for `task-error-*` and `task-failed-*` events. Routine template-miss screenshots are evidence, not separate triggers.
- Deduplicates using the incident filename, and suppresses repeated identical errors for ten minutes. Initializes by ignoring historical files unless `--include-existing` is supplied. Retries failed requests, preserving a durable local report for successful submissions.
- **Default is dry-run**. No API network requests, game clicks, repository edits, purchases, MSI builds or credentials.
- For real dispatch choose one backend:
  - `workspace-agent` requires a published ChatGPT Workspace Agent API channel (`agtch_...`) and a Workspace Agent access token. Availability depends on ChatGPT workspace features. The Workspace Agent must be configured to access the diagnostics and write results to a durable destination; triggers are asynchronous. There is no completed analysis in the trigger HTTP response.
  - `responses` uses the OpenAI Responses API with a separately billed API credential. It returns a written proposed diagnosis to a local JSON report, not a code change.
- By default it sends only event ID, type, activity, time, and source. **No screenshots, raw logs, passwords or account data are transmitted.** `--include-logs` allows redacted warning/error lines, `--include-images` allows PNG images for the Responses backend only.

## Test (no credentials)

```sh
python3 -m unittest discover -s tools/survivalops-agent-bridge -p 'test_*.py' -v
python3 tools/survivalops-agent-bridge/bridge.py --mode dry-run --once
```

First run marks existing incidents as seen. Subsequent `--once` calls analyze only *new* failures. A test run with historical diagnostics requires `--include-existing` and an empty temporary `--state-dir`.

## Live trigger (choose one)

For eligible ChatGPT Workspace Agents, provision an API channel and scope-limited token in ChatGPT, then store them **in secret environment configuration, not in Git**:

```sh
export CHATGPT_WORKSPACE_AGENT_TOKEN='...'
export CHATGPT_WORKSPACE_AGENT_CHANNEL='agtch_...'
python3 tools/survivalops-agent-bridge/bridge.py --mode workspace-agent --interval 15
```

Alternative using OpenAI Responses API:

```sh
export OPENAI_API_KEY='...'
export SURVIVALOPS_OPENAI_MODEL='gpt-5.4'
python3 tools/survivalops-agent-bridge/bridge.py --mode responses --interval 15
```

Save output to `~/.local/share/survivalops-agent-bridge/reports/`. Neither mode is auto-enabled, and never send credentials in chat. A production deployment also needs rate limiting, monitoring, and an approval gate before any ADB action, template activation, GitHub commit or updater release. Further AI repair automation must be tested against screenshots and replayable task traces first.
