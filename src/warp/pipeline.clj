(ns warp.pipeline
  (:import java.util.concurrent.TimeUnit
           java.nio.ByteOrder
           io.netty.handler.timeout.ReadTimeoutHandler
           io.netty.handler.codec.LengthFieldBasedFrameDecoder
           io.netty.handler.codec.LengthFieldPrepender))

(defn read-timeout
  [timeout]
  (ReadTimeoutHandler. (long timeout) TimeUnit/SECONDS))

(defn length-decoder
  []
  (LengthFieldBasedFrameDecoder. ByteOrder/BIG_ENDIAN 16384 0 4 0 4 true))

(defn length-encoder
  []
  (LengthFieldPrepender. ByteOrder/BIG_ENDIAN 4 0 true))
