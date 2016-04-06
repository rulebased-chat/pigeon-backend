(defproject pigeon-backend "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [clj-time "0.9.0"] ; required due to bug in lein-ring
                 [metosin/compojure-api "1.0.2"]
                 [metosin/ring-http-response "0.6.5"]
                 [ring-server "0.4.0"]
                 [environ "1.0.1"]
                 [ragtime "0.5.2"]
                 [org.clojure/tools.nrepl "0.2.10"]
                 [org.postgresql/postgresql "9.4.1207.jre7"]
                 [yesql "0.5.2"]
                 [ring-cors "0.1.7"]]
  :plugins       [[lein-environ "0.4.0"]]
  :ring {:handler pigeon-backend.handler/app}
  :uberjar-name "server.jar"
  :eval-in :nrepl
  :profiles {:dev [:project/dev :profiles/dev]
             :test [:project/test :profiles/test]
             :project/dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                                          [cheshire "5.3.1"]
                                          [ring-mock "0.1.5"]
                                          [midje "1.6.3"]
                                          [enlive "1.1.6"]
                                          [org.clojure/data.json "0.2.6"]]
                           :plugins [[lein-ring "0.9.6"]
                                     [lein-midje "3.1.3"]
                                     [lein-cloverage "1.0.6"]]
                           ;;when :nrepl-port is set the application starts the nREPL server on load
                           :env {:dev true
                                 :port 3000
                                 :nrepl-port 7000}}
             :project/test {:env {:test true
                                  :port 3001
                                  :nrepl-port 7001}}
             :uberjar {:omit-source true
                       :aot :all}}
   :aliases {"migrate" ["run" "-m" "pigeon-backend.db.migrations/migrate"]
             "rollback" ["run" "-m" "pigeon-backend.db.migrations/rollback"]}
   :main pigeon-backend.handler)
