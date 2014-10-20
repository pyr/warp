# Description:
#   Interacts with warp: the parallel execution commander
#
# Commands:
#   hubot warp me <scenario> - Schedules scenario for execution by warp
#   hubot engage! <scenario> - Schedules scenario for execution by warp
#
# Configuration:
#   HUBOT_WARP_URL - contains warp url
#   HUBOT_WARP_SHOW_URL - if necessary, a different url for display purposes
#

EventSource = require 'eventsource'

class Warp
  acks: 0
  ack_starting: 0
  acks_done: false
  done: 0

  constructor: (@scenario, @client) ->
    history = (process.env.HUBOT_WARP_SHOW_URL or process.env.HUBOT_WARP_URL) + '#/scenarios/' + @scenario
    @client.send "executing " + scenario + ", waiting 2 seconds for acks, reporting to: " + history

  process: (msg) ->

    if ((msg.type == 'resp' || msg.type == 'stop') && ! @acks_done)
      @acks_done = true
      @client.send @scenario + ": got " + @ack_starting + "/" + @acks + " positive acknowledgements"

    if (msg.type == 'ack')
      @acks++
      if (msg.msg.status == 'starting')
        @ack_starting++

    if (msg.type == 'resp')
      if (msg.msg.output.status == 'finished')
        @done++
        @client.send @scenario + ': ' + msg.msg.host + ': success (' + @done + '/' + @ack_starting + ')'
      if (msg.msg.output.status == 'failure')
        @done++
        @client.send @scenario + ': ' + msg.msg.host + ': failure! (' + @done + '/' + @ack_starting + ')'

      if (@done >= @ack_starting)
        @client.send @scenario + ": all done!"



module.exports = (robot) ->

  warp_url = process.env.HUBOT_WARP_URL

  response = (msg) ->

    scenario = msg.match[1]
      .split(/\ +/)
      .filter((a) -> a)
      .join("-")
    args = []
    if msg.match[2]
      args.push('profile=' + encodeURIComponent(msg.match[2]))

    if msg.match[3]
      args.push('matchargs=' + encodeURIComponent(arg)) for arg in msg.match[3].split(" ")

    if msg.match[4]
      args.push('args=' + encodeURIComponent(arg)) for arg in msg.match[4].split(" ")

    warp = new Warp(scenario, msg)
    url = warp_url + "/scenarios/" + scenario + "/executions"
    if args.length > 0
      url += '?' + args.join("&")
    console.log url

    es = new EventSource(url)
    es.onmessage = (e) ->
      warp.process JSON.parse(e.data)
    es.onerror = ->
      es.close()

  robot.respond /(?:warp me|engage!) (\S+)(?: to (\S+)(?: (\S+))?)?(?: with (.*))?$/i, response
  robot.respond /(\S+)(?: to (\S+)(?: (\S+))?)?(?: with (.*))?[,\.] [eE]ngage!$/i, response
  robot.hear /(\S+)(?: to (\S+)(?: (\S+))?)?(?: with (.*))?[,\.] [eE]ngage!$/i, response
