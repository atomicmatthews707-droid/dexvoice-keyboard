# Chain protocol (every scheduled step follows this)

This project is built by 7 scheduled sessions that fire about 20 minutes apart. Each session starts with NO memory. The repo is the only shared state: `chain/STATUS.json` (machine state) and `chain/PROGRESS.md` (human log). Files in `chain/steps/` hold each step's task.

You are step N (given in your launch prompt). Work alone on your own step, with total focus, and never redo other steps.

## 0. Set up the repo
1. `add_repo` for `atomicmatthews707-droid/dexvoice-keyboard` with access `push`, clone it (depth 1 is fine) to `/home/claude/dexvoice-keyboard`, call `register_repo_root`. Follow the add_repo tool's own instructions for cloning (one clone only, generous timeout). If the directory already exists and `git rev-parse HEAD` works, reuse it.
2. Always `git pull --rebase origin main` before reading STATUS.json and before every push. On push rejection: fetch, rebase, retry.

## 1. Check status (the "what's already running?" clause)
Read `chain/STATUS.json`. Times are UTC ISO strings. `now` = `date -u`.

- If `steps[N].state == "done"` → append nothing, exit. (A deferred twin already finished the work.)
- If ANY other step has `state == "running"` and its `updated` is less than 40 minutes ago → another session is still building. DO NOT start. Defer:
  - If `steps[N].deferrals >= 6`, stop deferring and treat the other session as dead (go to the next bullet).
  - Otherwise increment `steps[N].deferrals`, commit and push STATUS.json, then create ONE new scheduled task with `create_trigger`: `run_once_at` = now + 10 minutes, `initiation: "own_followup"`, same `name` as yours with " (retry)" appended, `notifications: {}`, and the exact same short launch prompt you were started with. Then exit without doing any work.
- If another step is `running` but `updated` is 40+ minutes old → it crashed. Set that step's state to `"failed"` and continue.
- If step N-1 (and any earlier step) is not `done` (pending or failed) → you must do the missing earlier step(s) first, in order, using their files in `chain/steps/`, before your own. Log that you did.

## 1b. Quota rule (usage limit / rate limit on Claude)
The owner's Claude usage quota refills at **9:00 PM America/Los_Angeles** on the day the chain runs (2026-10-29 21:00 PDT = 2026-10-30T04:00:00Z). A session that runs out of quota just dies mid-work; you will see that as a step stuck in `running` with a stale heartbeat, or as `quota_hit: true` in STATUS.json.
- If you are about to hit the limit (you get usage-limit / rate-limit errors from Claude itself, not from an external API): commit and push what you have, set `"quota_hit": true` and your step state back to `"pending"` in STATUS.json, push, then exit. The 9 PM resume task picks it up.
- If you find a crashed or quota-hit step and the current time is BEFORE 2026-10-30T04:00:00Z: do not try to catch up (the quota is almost certainly still empty). Make sure a resume is scheduled: if `resume_scheduled` is not true, create ONE scheduled task with `create_trigger` (`run_once_at` 2026-10-30T04:05:00Z, name "DeX Voice chain: resume after quota", `initiation: "own_followup"`, `notifications: {"push": true}`, prompt = the resume launch prompt in `chain/LAUNCH.md`), set `resume_scheduled: true`, push, and exit.
- If the current time is AFTER 2026-10-30T04:00:00Z and `quota_hit` is true again (quota ran out after the refill): STOP. Do not catch up, do not reschedule anything. Set `"stopped": true` with a reason in STATUS.json, add a line to PROGRESS.md, push, and make your RESULT line tell the owner the chain stopped because quota ran out again after 9 PM, which steps are done, and that he can restart it by asking Claude to resume the DeX Voice chain.
- If STATUS.json has `"stopped": true`, exit immediately without doing anything.

## 2. Claim and work
1. Set `steps[N].state = "running"`, `started` and `updated` = now. Commit and push IMMEDIATELY ("chain: step N running").
2. Read `chain/SPEC.md`, `chain/PROGRESS.md`, then `chain/steps/step-N.md`. Look at the existing code before writing; build on it.
3. Do the work. Commit and push at least every ~10 minutes, and each push must bump `steps[N].updated` (that is your heartbeat; other sessions rely on it).
4. Never ask questions or wait for input. If blocked, choose the most reasonable option, note it in PROGRESS.md, and move on. A task that truly needs the human (logging in, paying, installing on the phone) is skipped: add a line starting `HUMAN-ONLY:` to PROGRESS.md.
5. Never commit secrets or API keys.

## 3. Finish
1. Verify your step's "Done when" checks (build if possible, tests, grep the code).
2. Append 2-4 lines to `chain/PROGRESS.md` (what changed, what's left, decisions). Set `steps[N].state = "done"`, `updated` = now. Commit and push ("chain: step N done").
3. Your last output line starts with `RESULT:` and says in one sentence what was done.

## Local build note
Java 17 and Gradle are usually installed in the session, but the Android SDK usually is not. Step 1 tries once (10 minute cap) to install a minimal SDK (cmdline-tools, platform 34, build-tools 34). If it works, later steps run `gradle assembleDebug testDebugUnitTest` locally. If not, rely on careful code review and on GitHub Actions. Check CI with `gh run list --repo atomicmatthews707-droid/dexvoice-keyboard` if `gh` works; if it doesn't, write "CI result unverified" in PROGRESS.md.
