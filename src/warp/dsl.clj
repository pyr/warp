(ns warp.dsl
  (:require [clojure.string :as str]
            [stevedore.bash :as bash])
  (:refer-clojure :exclude [and or not]))

(defn timeout [seconds] {:timeout seconds})

(defn matcher [content] {:matcher content})
(defn and [& clauses] {:type :and :clauses clauses})
(defn or [& clauses] {:type :or :clauses clauses})
(defn not [clause] {:type :not :clause clause})
(defn host [host] {:type :host :host host})
(defn fact [fk fv] {:type :fact :fact fk :value fv})
(defn facts [facts]
  {:type :and :clauses (mapv #(fact (key %) (val %)) facts)})
(defn all []  {:type :all})
(defn none [] {:type :none})

(defn commands [& content] {:commands content})
(defn ping [] {:type :ping})
(defn sleep [seconds] {:type :sleep :seconds seconds})

(defn service*
  ([srv] (service* srv :status))
  ([srv action] {:type    :service
                 :service (name srv)
                 :action  (name action)}))
(defmacro service
  ([srv]
   `(service* (name (quote ~srv))))
  ([srv action]
   `(service* (name (quote ~srv)) (name (quote ~action)))))

(defn shell
  [& content]
  (reduce merge {:type :shell} content))


(defn exits
  [& exits]
  {:exits (vec (flatten exits))})

(defn cwd
  [dir]
  {:cwd dir})

(defmacro script
  [& forms]
  `{:shell (bash/script ~@forms)})

(defn scenario*
  [script-name directives]
  (reduce merge
          {:timeout  120
           :matcher  {:type :none}
           :profiles {}
           :name     (name script-name)}
          directives))

(defn profiles
  [& profiles]
  {:profiles
   (reduce merge {} profiles)})

(defmacro profile
  [pname content]
  (hash-map pname content))

(defmacro defscenario
  [sym & directives]
  (let [dirs (vec directives)]
    `(scenario* (quote ~sym) ~dirs)))

(defn read-strings
  "Returns a sequence of forms read from string."
  ([string]
   (read-strings []
                 (-> string (java.io.StringReader.)
                   (clojure.lang.LineNumberingPushbackReader.))))
  ([forms reader]
   (let [form (clojure.lang.LispReader/read reader false ::EOF false)]
     (if (= ::EOF form)
       forms
       (recur (conj forms (binding [*ns* (find-ns 'warp.dsl)] (eval form)))
              reader)))))

(defn load-scenario
  [path]
  (when-let [[_ sname] (re-matches #"(?i)^.*/(.*)\.wa?rp" path)]
    (scenario* (str/lower-case sname) (read-strings (slurp path)))))
