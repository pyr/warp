(ns org.spootnik.fleet.http
  (:require [ring.middleware.json      :refer [wrap-json-response
                                               wrap-json-body]]
            [compojure.core            :refer [routes GET POST PUT]]
            [clojure.tools.logging     :refer [error]]
            [clojure.core.async        :as async]
            [org.spootnik.fleet.api    :as api]
            [org.spootnik.fleet.engine :as engine]
            [org.httpkit.server        :as http]))

(defn api-routes
  [scenarios engine]

  (routes
   (GET "/scenarios" []
        {:body (api/all! scenarios)})

   (POST "/scenarios" {scenario :body}
         {:body
          (api/upsert! scenarios scenario)})

   (PUT "/scenarios" []
        (api/save! scenarios)
        {:body {:status "ok"}})

   (GET "/scenarios/:script_name" [script_name]
        {:body
         (api/get! scenarios script_name)})

   (POST "/scenarios/:script_name/executions" [script_name]
         (let [scenario (api/get! scenarios script_name)
               ch       (async/chan 10)]
           (engine/request engine scenario ch)
           {:body
            (async/<!! (async/reduce conj [] ch))}))))

(defn wrap-error
  [handler]
  (fn [request]
    (try (handler request)
         (catch Exception e
           (let [{:keys [status] :as data} (ex-data e)]
             (when (nil? data) (error e "unhandled exception"))
             {:status (or status 500)
              :body {:status (.getMessage e)}})))))

(defn start-http
  [scenarios engine opts]
  (api/load! scenarios)
  (http/run-server (-> (api-routes scenarios engine)
                       (wrap-error)
                       (wrap-json-response {:pretty true})
                       (wrap-json-body {:keywords? true}))
                   opts))
