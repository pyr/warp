(ns org.spootnik.om-fleet.ansi)

(def colors
  ["black"
   "red"
   "green"
   "yellow"
   "blue"
   "magenta"
   "cyan"
   "white"])

(def replacements
  {"&" "&amp;"
   "<" "&lt;"
   ">" "&gt;"})

(defn escape
  [text]
  (clojure.string/replace
    text
    (re-pattern (str "[" (clojure.string/join "" (keys replacements)) "]"))
    #(replacements %1)))

(defn linkify
  [text]
  (clojure.string/replace
    text
    #"(https?://[^\s]+)"
    "<a href=\"$1\">$1</a>"))

(defn within-bounds
  [[start end] value]
  (and (>= value start)
       (< value end)))

(defn style
  [code]
  (let [index (mod code 10)
        color (nth colors index)]
    (condp within-bounds code
      [0 1]     {:background nil :bold nil :color nil}
      [1 2]     {:bold "bold"}
      [30 38]   {:color color}
      [39 40]   {:color nil}
      [40 48]   {:background (str "background-" color)}
      [90 98]   {:color (str "bright-" color)}
      [100 108] {:background (str "background-bright-" color)})))

(defn get-classes
  [styles]
  (clojure.string/join " " (remove nil? (vals styles))))

(defn process-parts
  [styles [part & parts] done]
  (let [lines (clojure.string/split part "\n" -1)
        [_ codes text] (re-matches #"([\d;]+?)?m(.*)" (first lines))
        text           (str (or text (first lines))
                            (if (empty? (rest lines)) "" "\n")
                            (clojure.string/join "\n" (rest lines)))
        next-styles    (if (nil? codes)
                         {}
                         (apply merge (map (comp style int)
                                           (clojure.string/split codes ";"))))
        styles         (merge styles next-styles)
        classes        (get-classes styles)
        done           (if (empty? classes)
                         (str done text)
                         (str done (str "<span class=\"" classes "\">" text "</span>")))]
    (if (empty? parts)
      done
      (recur styles parts done))))

(defn highlight
  [text]
  (when-not (empty? text)
    (let [text (-> text
                   (escape)
                   (linkify))
          parts (clojure.string/split text #"\033\[")]
      (process-parts {} parts ""))))
