(ns warp.api
  (:require [com.stuartsierra.component :as com]
            [clojure.core.async         :as a]
            [cheshire.core              :as json]
            [warp.engine                :as engine]
            [warp.archive               :as archive]
            [warp.watcher               :as watcher]
            [net.http.server            :refer [run-server]]
            [bidi.bidi                  :refer [match-route*]]
            [bidi.ring                  :refer [resources-maybe redirect]]
            [clojure.tools.logging      :refer [info warn error]]))

(def ^:dynamic *engine* nil)
(def ^:dynamic *archive* nil)
(def ^:dynamic *watcher* nil)

(def prefix
  "Resource options"
  {:prefix "public/"})

(def api-routes
  ["/" [[""                                       (redirect "index.html")]
        ["api/" [["scenarios"  [[""               {:get  :list-scenarios}]
                                [["/" :id]        {:get  :get-scenario}]
                                [["/" :id "/run"] {:get  :start-execution}]]]
                 ["executions" [[""               {:get  :list-executions}]
                                [["/" :id]        {:get  :get-execution}]]]
                 ["replays"    [[["/" :id]        {:get  :get-replay}]]]]]
        [""                                       (resources-maybe prefix)]]])

(defn sse-event
  [body e]
  (a/put! body (format "data: %s\n\n" (json/generate-string e))))

(defn execution-stream-listener
  [body]
  (reify engine/ExecutionListener
    (publish-state [this {:keys [state] :as state}]
      (sse-event body {:type :state :state state})
      (when (= state :closed)
        (a/put! body "\n\n")
        (a/close! body)))
    (publish-event [this event]
      (sse-event body {:type :event :event event}))))

(defn match-route
  [{:keys [request-method uri] :as request}]
  (if (= request-method :error)
    (assoc request :handler :error)
    (match-route* api-routes uri request)))

(defn any->vec
  [o]
  (cond
    (nil? o)        nil
    (sequential? o) (vec o)
    :else           (vector o)))

(defn build-args
  [{:keys [get-params] :as request}]
  (let [profile   (some-> get-params :profile keyword)
        matchargs (any->vec (:matchargs get-params))
        args      (any->vec (:args get-params))]
    (cond-> {}
      profile   (assoc :profile   profile)
      matchargs (assoc :matchargs matchargs)
      args      (assoc :args      args))))

(defmulti dispatch :handler)

(defmethod dispatch :list-scenarios
  [request]
  {:status 200
     :headers {:content-type "application/json"}
   :body   (json/generate-string {:scenarios (watcher/all-scenarios *watcher*)})})

(defmethod dispatch :get-scenario
  [{:keys [route-params]}]
  (let [id       (:id route-params)
        scenario (watcher/by-id *watcher* id)
        replays  (archive/replays *archive* id)]
    {:status 200
     :headers {:content-type "application/json"}
     :body   (json/generate-string {:scenario scenario :replays replays})}))

(defmethod dispatch :get-replay
  [{:keys [route-params]}]
  (let [id     (:id route-params)
        replay (archive/replay *archive* id)]
    (info "replay: " (pr-str replay))
    {:status 200
     :headers {:content-type "application/json"}
     :body   (json/generate-string {:execution replay})}))

(defmethod dispatch :list-executions
  [request]
  {:status 200
   :headers {:content-type "application/json"}
   :body   (json/generate-string {:executions @(:executions *engine*)})})

(defmethod dispatch :start-execution
  [request]
  (let [body      (a/chan 10)
        listener  (execution-stream-listener body)
        scenario  (get-in request [:route-params :id])
        args      (build-args request)
        execution (engine/new-execution *engine* scenario listener args)]
    (info "will start" scenario)
    (sse-event body {:type :info :message "starting execution"})
    {:status  200
     :headers {:content-type      "text/event-stream"
               :x-accel-buffering "no"
               :cache-control     "no-cache"}
     :body    body}))


(defmethod dispatch :default
  [request]
  {:status 404
   :headers {:content-type "application/json"}
   :body (json/generate-string {:message "invalid route"})})

(defn wrap-dispatch
  [{:keys [handler] :as match} request]
  (cond (fn? handler)
        (handler request)

        (satisfies? bidi.ring/Ring handler)
        (bidi.ring/request handler request match)

        :else
        (dispatch match)))

(defn discard-content
  [request]
  (let [body (:body request)]
    (a/close! body)
    (assoc request :body "")))

(defn handler-fn
  [{:keys [archive watcher engine]}]
  (fn [request]
    (binding [*engine*  engine
              *watcher* watcher
              *archive* archive]
      (try
        (-> request
            (discard-content)
            (match-route)
            (wrap-dispatch request))
        (catch Exception e
          (let [{:keys [status message silence?]} (ex-data e)]
            (when-not silence?
              (error e "cannot process HTTP request"))
            {:status  (or status 500)
             :headers {:content-type "application/json"}
             :body    (json/generate-string
                       {:message (or message (.getMessage e))})}))))))

(defrecord Api [port options engine watcher archive server]
  com/Lifecycle
  (start [this]
    (let [srv (run-server (assoc options :port port)
                          (handler-fn this))]
      (assoc this :server srv)))
  (stop [this]
    (when server
      (server))
    (assoc this :server nil)))

(defn make-api
  [config]
  (map->Api config))
