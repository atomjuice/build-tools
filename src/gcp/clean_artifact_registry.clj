(ns gcp.clean-artifact-registry
  (:require [clojure.tools.cli :refer [parse-opts]]
            [cheshire.core :as json]
            [babashka.process :refer [shell]]))

(defn delete-artifact-image
  [image]
   (-> (shell {:out :string}
              (str "gcloud --quiet artifacts docker images delete " image " --delete-tags"))
       :out
       (json/parse-string true)))

(defn fetch-artifact-images
  ([] (fetch-artifact-images nil))
  ([image]
   (when image
     (-> (shell {:out :string}
                (str "gcloud artifacts docker images list " image " --format json --sort-by=~UPDATE_TIME"))
         :out
         (json/parse-string true)))))


(defn list-artifact-images [& args]
  (prn args)
  (let [opts  (parse-opts args [["-k" "--keep KEEP" "Number of recent images to keep"]
                                ["-i" "--image IMAGE" "image to clean up"]])
        command (first (:arguments opts))
        options (:options opts)]
    (prn (case command
           "list"
           (fetch-artifact-images (:image options))
           "clean"
           (do
             (->> (fetch-artifact-images (:image options))
                  (drop (Integer/parseInt (get options :keep "5")))
                  (mapv (fn remove-image [image]
                          (prn (str "Removing " (:package image) "@" (:version image)))
                          (delete-artifact-image (str (:package image) "@" (:version image)))))
                  (count)
                  (format "Removed %s images."))
             "finish")))))
