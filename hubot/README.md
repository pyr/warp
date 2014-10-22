hubot-warp: command and control from your chat room
===================================================

This hubot script allows you to control warp executions
from your chat room.

## Configuration

The script will rely on two variables:

- `HUBOT_WARP_URL`: Where to send warp requests to
- `HUBOT_WARP_SHOW_URL`: If necessary, what url to use for display

## Invocation

Hubot understands the following invocation:

- `hubot: warp me <command>`
- `hubot: engage! <command>`
- `<command>, engage!`

## Command specification

Warp commands may have a profile (with an optional arg) supplied as well
as parameters. The full command specification is:

```
command (to profile param) (with param1 param2)
```

For instance, if you have a parameterized *platform* profile for the deploy command, you would say:

```
deploy to platform staging, engage!
```
