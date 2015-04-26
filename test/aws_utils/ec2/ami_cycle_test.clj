(ns aws-utils.ec2.ami-cycle-test
  (:require [clojure.test :refer :all]
            [aws-utils.ec2.ami-cycle :refer :all]))

(deftest ut-get-instance-name
  (testing "get-instance-name."
    (is (= "Test Instance 4 aws-utils" (get-instance-name "i-8d061c94")))
  )
)

(deftest ut-create-image
  (testing "create-image."
   (is (not (nil? (:image-id (create-image "i-8d061c94" true)))))
  )
)

(deftest ut-list-related-ami
  (testing "list-related-ami."
    (is (every? #(re-seq (re-pattern (str "^" (get-instance-name "i-8d061c94") "-\\d{14}$")) (:name %)) (list-related-ami "i-8d061c94")))
  )
)

(deftest ut-is-available-image?
  (testing "ut-is-available-image?"
    (is (not (is-available-image? "aho")))
  )
)
