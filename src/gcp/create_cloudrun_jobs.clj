(ns gcp.create-cloudrun-jobs
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as str]
            [cheshire.core :as json]
            [babashka.process :refer [shell]]))


(def gcp (read-string (slurp "./bb/cronjobs.edn")))

(defn execute
  [cmd debug]
  (if (true? debug)
    (prn cmd)
    (try
      (shell cmd)
      (catch Exception e
        (prn (:err e))
         (prn (:cmd e))))))

(defn build-cloud-run-job-url
  ([environment name] (build-cloud-run-job-url :project-id environment name  ))
  ([key environment name]
   (str
    "https://" (:region environment) "-run.googleapis.com/apis/run.googleapis.com/v1/namespaces/" (get environment key) "/jobs/" name)))


(defn fetch-cloud-job-list [environment]
  (map (fn [job]
         (get-in job [:metadata :name]))
       (-> (shell {:out :string} (str "gcloud beta run jobs list --format=json --project="  (:project-name environment)))
           :out
           (json/parse-string true))))


(defn fetch-cloud-job-schedule-list [environment]
  (map (fn [job]  (get-in job [:metadata :name]))
       (-> (shell {:out :string} (str "gcloud scheduler jobs list --format=json --project="  (:project-name environment)))
           :out
           (json/parse-string true))))


(defn cloud-run-job-exists? [job existing-jobs]
  (some (fn [j]
          (= job j)) existing-jobs))

(defn cloud-run-job-schedule-exists? [job existing-jobs-schedules]
  (some (fn [j]
          (= job j)) existing-jobs-schedules))

(defn create-cloud-jobs-cmd [update environment {:keys [name args] :as job}]
  (str "gcloud beta run jobs "
       (if update "update " "create ")
       name
       " --project=" (:project-name environment)
       " --region=" (:region environment)
       " --service-account=" (:service-account environment)
       " --set-secrets=" (get environment :secrets-path "/app/environment/.env") "=" (:secrets environment)
       " --vpc-egress=all-traffic"
       " --vpc-connector=" (-> environment :vpc-connector)
       " --image=" (or (:image job) (:image environment))
       " --command=" (:cmd job)
       " --memory=" (or (:memory job) "512Mi")
       " --cpu=1"
       (when args
         (str " --args=" (str/join " --args=" args)))))


(defn create-cloud-schedule-cmd [update environment {:keys [name schedule] :as job}]
  (str "gcloud scheduler jobs " (if update "update " "create ") "http "
       name
       " --project=" (:project-name environment)
       " --schedule \"" schedule  "\""
       " --location=" (:region environment)
       " --http-method=post"
       " --uri=\"" (build-cloud-run-job-url :project-name environment name) ":run" "\""
       " --oauth-service-account-email=\"" (:service-account environment ) "\""
       " --oauth-token-scope=\"https://www.googleapis.com/auth/cloud-platform\""))

(defn install-cronjobs
  ([] (prn "Please supply environment with -e"))
  ([& args]
   (let [options (:options (parse-opts args [["-e" "--environment ENVIRONMENT" "Environment"]
                                             ["-d" "--debug DEBUG" "Debug"]]))
         environment (keyword (get options :environment "staging"))
         debug (boolean (get options :debug false))
         existing-jobs-names (fetch-cloud-job-list (get-in gcp [:cron environment]))]
     (->> (:jobs gcp)
          (filter (fn [[k {:keys [name] :as j}]]
                    (not (cloud-run-job-exists? name existing-jobs-names))))

          (mapv (fn process-cronjobs [[job-key job]]
                  (prn (str "exists ?"
                            (cloud-run-job-exists? (:name job) existing-jobs-names)))
                  (execute (create-cloud-jobs-cmd
                            false
                            (get-in gcp [:cron environment])
                            job) debug)

                  (execute (create-cloud-schedule-cmd
                            false
                            (get-in gcp [:cron environment])
                            job)
                           debug)))))))
