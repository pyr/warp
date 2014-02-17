var app = angular.module('fleet', ['ngRoute', 'ngSanitize']);

app.filter('ansi', function($sce) {
    return function (input) {
        return $sce.trustAsHtml(window.ansi_up.ansi_to_html(
            window.ansi_up.escape_for_html(input)));
    };
});

app.factory('$scenario', function ($http, $timeout) {
   return {
       create: function (scenario) {
           return $http.post('/scenarios', scenario);
       },
       list: function() {
           return $http.get('/scenarios');
       },
       delete: function(script_name) {
           return $http.delete('/scenarios/' + script_name);
       },
       execute: function (scope, script, script_name) {
           var url = '/scenarios/' + script_name + '/executions';
           var es = new EventSource(url);
           var results = {done: false,
                          script_name: script_name,
                          script: script,
                          hosts: {},
                          host_commands: {},
                          statuses: {},
                          total_acks: 0,
                          starting_hosts: 0,
                          total_done: 0,
                          acks: []};

           es.onconnect = function () {
               return;
           };
           es.onerror = function (data, why) {
               results.done = true;
               es.close();
           };
           es.onclose = function () {
             results.done = true;
           };
           es.onmessage = function (payload) {
               $timeout(function() {

                   var msg = JSON.parse(payload.data);

                   if (msg.type == 'ack') {
                       if (msg.msg.status == 'starting') {
                           results.starting_hosts += 1;
                       }
                       results.total_acks += 1;
                       results.acks.push(msg.msg);
                   } else if (msg.type == 'resp') {
                       var host = msg.msg.host;
                       var finished = ((msg.msg.output.status == 'failure') ||
                                       (msg.msg.output.status == 'finished'));

                       if (!results.hosts[host])
                           results.hosts[host] = [];
                       results.hosts[host].push(msg.msg.output);

                       if (finished)
                           results.total_done += 1;

                       if (results.total_done >= results.starting_hosts)
                           results.done = true;

                       results.host_commands[host] = _.zip(results.script, results.hosts[host]);
                   } else {
                       console.log('unknown payload type', msg);
                   }
               });
           };
           return results;
       }
   };
});

app.controller('Fleet', function($scope, $routeParams, $location, $scenario) {

    $scope.get_color = function(c) {
        if (c == 'finished') {
            return 'label-primary';
        } else if (c == 'failure') {
            return 'label-danger';
        } else if (c == 'success') {
            return 'label-success';
        } else {
            return 'label-warning';
        }
    };

    $scope.scenarios = {};

    $scope.empty_body = _.isEmpty;

    $scenario.list().success(function (data) {
        $scope.scenarios = data;
    });

    if ($routeParams.script_name) {
        $scope.script_name = $routeParams.script_name;
    }

    if ($routeParams.host) {
        $scope.host = $routeParams.host;
    }

    $scope.set_host = function(host) {
        $scope.host = host;
    }

    $scope.scenario_list = function() {
        var l = [];
        for (var k in $scope.scenarios) {
            l.push($scope.scenarios[k]);
        }
        return l;
    };

    $scope.create_scenario = function (scenario) {
        $scenario.create(scenario).success(function (data) {
            $scope.scenarios = data;
            $location.path('/scenarios');
        });
    };

    $scope.delete_scenario = function (script_name) {
        $scenario.delete(script_name).success(function (data) {
            $scope.scenarios = data;
            $location.path('/scenarios');
        });
    };

    $scope.execute_scenario = function (script_name) {
        $scope.execution_scheduled = $scope.scenarios[script_name];
        $scope.execution = $scenario.execute($scope, $scope.scenarios[script_name].script, script_name);
    };
});

app.config(function($routeProvider) {
    $routeProvider
        .when('/list', {templateUrl: 'listing.html', controller: 'Fleet'})
        .when('/details/:script_name', {templateUrl: 'details.html', controller: 'Fleet'})
        .when('/host/:host', {templateUrl: 'host.html', controller: 'Fleet'})
        .when('/post', {templateUrl: 'post.html', controller: 'Fleet'})
        .otherwise({redirectTo: '/list'});
});
