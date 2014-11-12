(ns org.spootnik.warp.http
  (:require [compojure.core                 :refer [routes GET POST PUT DELETE]]
            [clojure.tools.logging          :refer [error infof]]
            [clojure.pprint                 :refer [pprint]]
            [ring.util.response             :refer [response redirect]]
            [cheshire.core                  :refer [generate-string]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params         :refer [wrap-params]]
            [clojure.core.async             :refer [put! close! chan <!! alts!! timeout pub sub]]
            [ring.middleware.json           :as json]
            [compojure.route                :as route]
            [org.spootnik.warp.api          :as api]
            [org.spootnik.warp.engine       :as engine]
            [org.spootnik.warp.history      :as history]
            [qbits.jet.server               :as http]
            [clojure.tools.logging          :refer [info]]
            [ring.middleware.cors           :refer [wrap-cors cors-hdrs]]))

(defn json-response
  [body]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (generate-string body)})

(defn yield-msg
  [chan]
  (alts!! [chan (timeout 25000)]))

(defn api-routes
  [scenarios engine {:keys [origins] :or {origins []}}]

  (let [patterns    (mapv re-pattern origins)
        publisher   (chan)
        publication (pub publisher #(:topic %))]
    (routes
     (GET "/history" []
          (json-response {:done @history/done
                          :in-progress @history/in-progress}))

     (GET "/scenarios" []
          (json-response (api/all! scenarios)))

     (POST "/scenarios" {scenario :body}
           (json-response (api/upsert! scenarios scenario)))

     (PUT "/scenarios" []
          (json-response (api/save! scenarios)))

     (GET "/scenarios/:script_name" [script_name]
          (json-response (api/get! scenarios script_name)))

     (DELETE "/scenarios/:script_name" [script_name]
             (json-response (api/delete! scenarios script_name)))

     (GET "/scenarios/:script_name/history" [script_name]
          (json-response (history/fetch scenarios script_name)))

     (GET "/scenarios/:script_name/executions" request
          (let [script_name (-> request :params :script_name)
                stream      (not= "false" (get-in request [:params :stream] "true"))
                args        (if-let [args (-> request :params :args)]
                              (if (sequential? args) args [args])
                              [])
                matchargs   (if-let [args (-> request :params :matchargs)]
                              (if (sequential? args) args [args])
                              [])
                scenario    (api/get! scenarios script_name)
                profile     (-> request :params :profile keyword)
                ch          (chan 10)
                resp        (chan 10)
                headers     {"Content-Type" "text/event-stream"
                             "X-Accel-Buffering" "no"
                             "Cache-Control" "no-cache"}
                origin      (get-in request [:headers "origin"])
                valid-cors  (and origin (some #(re-find % origin) patterns))
                headers     (if valid-cors
                              (merge (cors-hdrs (request :headers)) headers)
                              headers)]

            (future
              (try
                (doseq [msg (repeatedly #(<!! ch))
                        :while msg
                        :when msg]
                  (history/update script_name msg)
                  (when stream
                    (put! resp (format "data: %s\n\n" (generate-string msg))))
                  (put! publisher (assoc msg :topic :events)))
                (catch Exception e
                  (error e "cannot handle incoming message"))
                (finally
                  (close! resp))))

            (infof "sending request for %s [profile:%s] [matchargs:%s] [args:%s]"
                   scenario profile matchargs args)
            (let [data (engine/request
                         engine
                         (api/prepare scenario profile matchargs args)
                         ch)]
              (put! publisher (assoc data
                                     :topic :events
                                     :type :start
                                     :scenario script_name))
              (if stream
                {:status 200
                 :headers headers
                 :body resp}
                (json-response data)))))

     (GET "/events" request
          (let [channel    (chan 10)
                resp       (chan 10)
                headers    {"Content-Type" "text/event-stream"
                            "X-Accel-Buffering" "no"
                            "Cache-Control" "no-cache"}
                origin     (get-in request [:headers "origin"])
                valid-cors (and origin (some #(re-find % origin) patterns))
                headers    (if valid-cors
                             (merge (cors-hdrs (request :headers)) headers)
                             headers)]

            (sub publication :events channel)

            (future
              (try
                (doseq [[msg source] (repeatedly #(yield-msg channel))
                        :while (or (not= channel source) msg)
                        :let [msg (or msg {:type "keepalive"})
                              msg (dissoc msg "topic" :topic)
                              data (format "data: %s\n\n" (generate-string msg))]]
                  (let [open (put! resp data)]
                    (when-not open (throw (ex-info "client disconnect"
                                                   {:type :client-disconnect})))))
                (catch Exception e
                  (let [{:keys [type]} (ex-data e)]
                    (condp = type
                      :client-disconnect
                      (info "client disconnect")

                      (error e "cannot handle incoming message"))))
                (finally
                  (close! channel)
                  (close! resp))))

            {:status 200
             :headers headers
             :body resp}))

     ;; defaults
     (GET "/" []      (redirect "/index.html"))
     (route/resources "/")
     (route/not-found {:status 404 :body
                       (generate-string {:status "no such resource"})}))))

(defn wrap-error
  [handler]
  (fn [request]
    (try (handler request)
         (catch Exception e
           (let [{:keys [type status] :as data} (ex-data e)]
             (error e "got exception")
             {:status (or status 500)
              :body (generate-string {:status (.getMessage e)
                                      :data data})})))))

(defn yield-cors-match
  [{:keys [origins] :or {origins []}}]
  (let [patterns (mapv re-pattern origins)]
    (fn [origin]
      (and origin (some #(re-find % origin) patterns)))))

(defn start-http
  [scenarios engine opts]
  (api/load! scenarios)
  (let [handler (-> (api-routes scenarios engine opts)
                    (wrap-error)
                    (wrap-keyword-params)
                    (wrap-params)
                    (wrap-cors (yield-cors-match opts))
                    (json/wrap-json-body {:keywords? true}))]
    (http/run-jetty (merge opts {:ring-handler handler}))))