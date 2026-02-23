(ns equipment.lets
  (:require
   [clojure.string :as str]
   [clojure.tools.build.api :as b]))

(defn os []
  (let [os-name (str/lower-case (System/getProperty "os.name"))]
    (cond
      (str/starts-with? os-name "windows") :windows
      ;;   (str/starts-with? os-name "mac")     :macos ;; no machine to try this
      (str/starts-with? os-name "linux")   :linux
      :else
      (throw (ex-info (str "os not supported yet! os: " os) {})))))

(defn decided-basis []
  (let [os-alias (os)]
    (b/create-basis {:project "deps.edn" :aliases [:jvm-game :repl os-alias]})))

(defn repl [& _]
  (println "running desktop game with repl...")
  (let [cmd (b/java-command {:basis (decided-basis) :main 'clojure.main :main-args ["-m" "minusthree.-dev.start"]})]
    (b/process cmd)))
