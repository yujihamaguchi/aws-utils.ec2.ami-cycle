(ns aws-utils.ec2.ami-cycle
  (:require [amazonica.core :refer :all]
            [amazonica.aws.ec2 :as ec2]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clj-time.local :as tl]
            [clojure.tools.logging :as log])
  (:gen-class))

; <FIXME>
(defcredential
  ""
  ""
  "ap-northeast-1")

; <FIXME>
(def owner-id "")

; <TODO>
; ロギング
; - インスタンス名のprefixをログに付けたい
; エラー処理
; テスト

(defn get-instance-name [instance-id]
  (try
    (->> [instance-id]
         (ec2/describe-instances :instance-ids)
         (:reservations)
         (first)
         (:instances)
         (first)
         (:tags)
         ((partial filter #(= "Name" (:key %))))
         (first)
         (:value))
    (catch Exception err
      (log/error (ex->map err)))))

(defn create-image [instance-id is-no-reboot]
  (try
    (let [local-date (->> (tl/local-now)
                          (tf/unparse (tf/formatter-local "yyyyMMddHHmmss")))
          image-name (str (get-instance-name instance-id) "-" local-date)]
      (ec2/create-image
        :instance-id instance-id
        :no-reboot is-no-reboot
        :name image-name))
    (catch Exception err
      (log/error (ex->map err)))))

(defn get-linked-amis [instance-id]
  (try
    (->> (ec2/describe-images :owners [owner-id])
         (:images)
         (filter #(re-seq (re-pattern (str "^" (get-instance-name instance-id) "-\\d{14}$")) (:name %))))
    (catch Exception err
      (log/error (ex->map err)))))

(defn take-older-ami [require-ami-cnt amis]
  (try
    (->> amis
         (sort #(compare (:name %) (:name %2)))
         ((fn [coll] (take (- (count coll) require-ami-cnt) coll))))
    (catch Exception err
      (log/error (ex->map err)))))

(defn get-linked-snapshot-ids [ami]
  (try
    (map #(:snapshot-id (:ebs %)) (:block-device-mappings ami))
    (catch Exception err
      (log/error (ex->map err)))))

(defn get-image-state [image-id]
  (try
    (:state (first (:images (ec2/describe-images :owners [owner-id] :image-ids [image-id]))))
    (catch Exception err
      (log/error (ex->map err)))))

(defn ami-cycle [instance-id require-ami-count]
  ; 新規AMIを取得
  (log/info "AMIサイクル処理開始")
  (let [image-id (:image-id (create-image instance-id true))]
    (log/info "Create image: " image-id)
    (while (not (= "available" (get-image-state image-id)))
      (Thread/sleep 10000)))
  (when-let [deregister-amis (seq (take-older-ami (read-string require-ami-count) (get-linked-amis instance-id)))]
    (when-let [delete-snapshot-ids (seq (mapcat get-linked-snapshot-ids deregister-amis))]
      (log/info "Deregister image IDs: " (map #(:image-id %) deregister-amis))
      (log/info "Delete snapshot IDs: " delete-snapshot-ids)
      (try
        (doall (pmap #(ec2/deregister-image :image-id (:image-id %)) deregister-amis))
        (doall (pmap #(ec2/delete-snapshot :snapshot-id %) delete-snapshot-ids))
      (catch Exception err
        (log/error (ex->map err))))))
  (log/info "AMIサイクル処理終了"))

(defn -main [& args]
  (apply ami-cycle args))

