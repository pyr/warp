# Description:
#   Interacts with warp: the parallel execution commander
#
# Commands:
#   hubot <scenario>, 🚀  - Schedules scenario for execution by warp
#   hubot <scenario>, rocket! - Schedules scenario for execution by warp
#
# Configuration:
#   HUBOT_WARP2_URL - contains warp url
#   HUBOT_WARP2_SHOW_URL - if necessary, a different url for display purposes
#

EventSource = require 'eventsource'
c = require('irc-colors')

class WarpHost
  reported: 0
  total: 0
  success: true
  finished: false
  constructor: (@host, @scenario, @client, @index) ->
    @finished = false
  step: (success) ->
    @success =  success
  done: (should_show, index) ->
    @finished = true
    if should_show
      if @success
        @client.send @scenario + ': ' + c.bold(@host) + ': ' + c.green('success')     + ' (' + @index + '/' + @total + ')'
      else
        @client.send @scenario + ': ' + c.bold(@host) + ': ' + c.red.bold('failure!') + ' (' + @index + '/' + @total + ')'
   ack_timeout: (total) ->
     @total = total
     if @finished
       @done true
   timeout: () ->
     @client.send @scenario + ': ' + c.bold(@host) + ': ' + c.red.bold('timed out!') + ' (' + @index + '/' + @total + ')'


class Warp2
  running: 0
  acked: 0
  success: 0
  done: 0
  statuses: undefined
  ack_timeout: 0

  constructor: (@scenario, @client) ->
    @statuses = new Object()
    @client.send "executing " + c.blue(scenario)

  process: (msg) ->

    if msg.type == 'event' && msg.event.opcode == 'init'
      history = (process.env.HUBOT_WARP2_SHOW_URL or process.env.HUBOT_WARP2_URL) + '#/replay/' + msg.event.sequence
      @client.send @scenario + ": reporting to: " + c.blue(history)
    else if msg.type == 'event' && msg.event.opcode == 'ack-timeout'
      @ack_timeout = 1
      @client.send @scenario + ": got " + @running + "/" + @acked + " positive acks"
      status.ack_timeout(@running) for host,status of @statuses
    else if msg.type == 'event' && msg.event.opcode == 'timeout'
      @client.send "scenario timeout reached"
    else if msg.type == 'state'
      if msg.state == 'closed'
        status.timeout for host,status of @statuses
        @client.send @scenario + ": all done!"
    else if msg.type == 'event' && msg.event.opcode == 'command-start'
      @running++
      @acked++
      @statuses[msg.event.host] = new WarpHost(msg.event.host, @scenario, @client, @running)
    else if msg.type == 'event' && msg.event.opcode == 'command-deny'
      @acked++
    else if msg.type == 'event' && msg.event.opcode == 'command-end'
      @done++
      @statuses[msg.event.host].done(@ack_timeout)
    else if msg.type == 'event' && msg.event.opcode == 'command-step'
      @done++
      @statuses[msg.event.host].step(msg.event.output.success)
    else
     @client.send @scenario + ": unknown payload: " + msg

module.exports = (robot) ->

  warp_url = process.env.HUBOT_WARP2_URL

  response = (msg, scenario, profile, margs, pargs) ->

    scenario = scenario
      .split(/\ +/)
      .filter((a) -> a)
      .join("-")
    args = []
    if profile
      args.push('profile=' + encodeURIComponent(profile))

    if margs
      args.push('matchargs=' + encodeURIComponent(arg)) for arg in margs.split(" ")

    if pargs
      args.push('args=' + encodeURIComponent(arg)) for arg in pargs.split(" ")
    scenario = scenario
      .split(/\ +/)
      .filter((a) -> a)
      .join("-")

    warp = new Warp2(scenario, msg)
    url = warp_url + "/api/scenarios/" + scenario + "/run"
    if args.length > 0
      url += '?' + args.join("&")
    console.log('warp2 url: ' + url)

    es = new EventSource(url)
    es.onmessage = (e) ->
      warp.process JSON.parse(e.data)
    es.onerror = (e) ->
      es.close()

  handle = (msg) ->
    mo = msg.match[1].match /(.+?)(?: to (\S+)(?: ([\S ]+?))?)?(?: with (.*))$/i
    if mo
      return response msg, mo[1], mo[2], mo[3], mo[4]
    mo = msg.match[1].match /(.+?)(?: to (\S+)(?: ([\S ]+?))?)?$/i
    if mo
      return response msg, mo[1], mo[2], mo[3]

  robot.hear    /(?:hubot:? *)?(.*)[,\.] [eE]ngage ?!$/i, handle
  robot.hear    /(?:hubot:? *)?(.*)[,\.] 🚀$/i, handle
