(ns asciinema-player.vt
  (:require [clojure.string :as string]))

;; References:
;; http://en.wikipedia.org/wiki/ANSI_escape_code
;; http://ttssh2.sourceforge.jp/manual/en/about/ctrlseq.html
;; http://real-world-systems.com/docs/ANSIcode.html
;; http://www.shaels.net/index.php/propterm/documents
;; http://manpages.ubuntu.com/manpages/lucid/man7/urxvt.7.html
;; http://vt100.net/docs/vt102-ug/chapter5.html

(defn make-vt []
  {:parser {:state :ground
            :private-marker nil
            :intermediate-chars []
            :param-chars []}
   :cursor {:x 0 :y 0 :visible true}
   :lines []})

;; actions

(defn ignore [vt input]
  vt)

(defn print [vt input]
  (update-in vt [:cursor :x] inc))

(defn execute [vt input]
  vt)

(defn clear [vt input]
  (update-in vt [:parser] merge {:private-marker nil :intermediate-chars [] :param-chars []}))

(defn collect [vt input]
  vt)

(defn param [vt input]
  vt)

(defn esc-dispatch [vt input]
  vt)

(defn csi-dispatch [vt input]
  vt)

(defn hook [vt input]
  vt)

(defn put [vt input]
  vt)

(defn unhook [vt input]
  vt)

(defn osc-start [vt input]
  vt)

(defn osc-put [vt input]
  vt)

(defn osc-end [vt input]
  vt)

;; end actions

(defn- event-seq [event]
  (if (keyword? event)
    (let [[low high] (string/split (name event) "-")
          low (js/parseInt low 16)
          high (js/parseInt high 16)]
      (range low (inc high)))
    [event]))

(defn- events [& items]
  (set (mapcat event-seq items)))

(def c0-prime? (events :0x00-0x17 0x19 :0x1C-0x1F))

(def anywhere-state {(events 0x18 0x1A :0x80-0x8F :0x91-0x97 0x99 0x9A) {:action execute, :transition :ground}
                     (events 0x9C) {:transition :ground}
                     (events 0x1B) {:transition :escape}
                     (events 0x98 0x9E 0x9F) {:transition :sos-pm-apc-string}
                     (events 0x90) {:transition :dcs-entry}
                     (events 0x9D) {:transition :osc-string}
                     (events 0x9B) {:transition :csi-entry}})

(def states {
  :ground {
    c0-prime? {:action execute}
    (events :0x20-0x7F :0xA0-0xFF) {:action print}
  }
  :escape {
    :on-enter clear
    c0-prime? {:action execute}
    (events :0x20-0x2F) {:action collect, :transition :escape-intermediate}
    (events :0x30-0x4F :0x51-0x57 0x59 0x5A 0x5C :0x60-0x7E) {:action esc-dispatch, :transition :ground}
    (events 0x5B) {:transition :csi-entry}
    (events 0x5D) {:transition :osc-string}
    (events 0x50) {:transition :dcs-entry}
    (events 0x58 0x5E 0x5F) {:transition :sos-pm-apc-string}
    (events 0x7f) {:action ignore}
  }
  :escape-intermediate {
    c0-prime? {:action execute}
    (events :0x20-0x2F) {:action collect}
    (events :0x30-0x7E) {:action esc-dispatch, :transition :ground}
    (events 0x7f) {:action ignore}
  }
  :csi-entry {
    :on-enter clear
    c0-prime? {:action execute}
    (events :0x40-0x7E) {:action csi-dispatch, :transition :ground}
    (events :0x30-0x39 0x3B) {:action param, :transition :csi-param}
    (events :0x3C-0x3F) {:action collect, :transition :csi-param}
    (events 0x3A) {:transition :csi-ignore}
    (events :0x20-0x2F) {:action collect, :transition :csi-intermediate}
    (events 0x7f) {:action ignore}
  }
  :csi-param {
    c0-prime? {:action execute}
    (events :0x30-0x39 0x3B) {:action param}
    (events 0x3A :0x3C-0x3F) {:transition :csi-ignore}
    (events :0x20-0x2F) {:action collect, :transition :csi-intermediate}
    (events :0x40-0x7E) {:action csi-dispatch, :transition :ground}
    (events 0x7f) {:action ignore}
  }
  :csi-intermediate {
    c0-prime? {:action execute}
    (events :0x20-0x2F) {:action collect}
    (events :0x40-0x7E) {:action csi-dispatch, :transition :ground}
    (events :0x30-0x3F) {:transition :csi-ignore}
    (events 0x7f) {:action ignore}
  }
  :csi-ignore {
    c0-prime? {:action execute}
    (events :0x20-0x3F) {:action ignore}
    (events :0x40-0x7E) {:transition :ground}
    (events 0x7f) {:action ignore}
  }
  :dcs-entry {
    :on-enter clear
    c0-prime? {:action ignore}
    (events :0x20-0x2F) {:action collect, :transition :dcs-intermediate}
    (events 0x3A) {:transition :dcs-ignore}
    (events :0x30-0x39 0x3B) {:action param, :transition :dcs-param}
    (events :0x3C-0x3F) {:action collect, :transition :dcs-param}
    (events :0x40-0x7E) {:transition :dcs-passthrough}
    (events 0x7f) {:action ignore}
  }
  :dcs-param {
    c0-prime? {:action ignore}
    (events :0x20-0x2F) {:action collect, :transition :dcs-intermediate}
    (events :0x30-0x39 0x3B) {:action param}
    (events 0x3A :0x3C-0x3F) {:transition :dcs-ignore}
    (events :0x40-0x7E) {:transition :dcs-passthrough}
    (events 0x7f) {:action ignore}
  }
  :dcs-intermediate {
    c0-prime? {:action ignore}
    (events :0x20-0x2F) {:action collect}
    (events :0x30-0x3F) {:transition :dcs-ignore}
    (events :0x40-0x7E) {:transition :dcs-passthrough}
    (events 0x7f) {:action ignore}
  }
  :dcs-passthrough {
    :on-enter hook
    c0-prime? {:action put}
    (events :0x20-0x7E) {:action put}
    (events 0x7f) {:action ignore}
    :on-exit unhook
  }
  :dcs-ignore {
    c0-prime? {:action ignore}
    (events :0x20-0x7f) {:action ignore}
  }
  :osc-string {
    :on-enter osc-start
    c0-prime? {:action ignore}
    (events :0x20-0x7F) {:action osc-put}
    (events 0x07) {:transition :ground} ; 0x07 is xterm non-ANSI variant of transition to :ground - THIS WON'T HAPPEN BECAUSE OF 0x07 IN ANYWHERE, REMOVE?
    :on-exit osc-end
  }
  :sos-pm-apc-string {
    c0-prime? {:action ignore}
    (events :0x20-0x7F) {:action ignore}
  }
})

(defn- get-transition [rules input]
  (some (fn [[pred cfg]] (when (pred input) cfg)) rules))

(defn parse [current-state input]
  (let [current-state-cfg (get states current-state)
        transition (or (get-transition anywhere-state input) (get-transition current-state-cfg input))]
    (if transition
      (let [transition-action (:action transition)]
        (if-let [new-state (:transition transition)]
          (let [new-state-cfg (get states new-state)
                exit-action (:on-exit current-state-cfg)
                entry-action (:on-enter new-state-cfg)
                actions (remove nil? [exit-action transition-action entry-action])]
            [new-state actions])
          [current-state (if transition-action [transition-action] [])])))))
