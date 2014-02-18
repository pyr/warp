fleet: parallel scenario execution
==================================

fleet works hand in hand with any number of
[fleet-agent's](https://github.com/pyr/fleet-agent), schedules
scenarios for execution.

scenario creation an execution happens through an HTTP api,
a command-line client is coming up.

fleet and [fleet-agent](https://github.com/pyr/fleet-agent) borrow
from [mcollective](http://puppetlabs.com/mcollective) and my first
implementation within [amiral](https://github.com/pyr/amiral)

fleet aims to improve on amiral in the following ways:

- Bundle matchers, timeouts and a list of commands (a script) options
  in named "scenarios"
- Extract out of the IRC bot framework and display extended execution
  results in a web view
- Deprecate signing requests with ssh-keys and move to SSL
- Provide a lighter-weight agent
- Support arguments

