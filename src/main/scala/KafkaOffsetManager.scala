import kafka.common.TopicAndPartition
import kafka.message.MessageAndMetadata
import kafka.serializer.StringDecoder
import kafka.utils.{ZKGroupTopicDirs, ZkUtils}
import org.I0Itec.zkclient.ZkClient
import org.apache.spark.rdd.RDD
import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.dstream.InputDStream
import org.apache.spark.streaming.kafka.{HasOffsetRanges, KafkaUtils}

object KafkaOffsetManager {

  def readOffsetsAndCreateDStream(ssc: StreamingContext,
                                  kafkaParams: Map[String, String],
                                  zkClient: ZkClient,
                                  group: String,
                                  zkTopicPath: String,
                                  topic: String): InputDStream[(String, String)] = {
    val children = zkClient.countChildren(zkTopicPath)
    var fromOffSets: Map[TopicAndPartition, Long] = Map()
    val kafkaStream = if (children > 0) {
      for (i <- 0 until children) {
        val partitionOffset = zkClient.readData[String](s"$zkTopicPath/${i}")
        val tp = TopicAndPartition(topic, i)
        fromOffSets += (tp -> partitionOffset.toLong)
      }
      val messageHandler = (mmd: MessageAndMetadata[String, String]) => (mmd.key, mmd.message)
      KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder, (String, String)](ssc, kafkaParams, fromOffSets, messageHandler)
    } else {
      val topics = Set(topic)
      KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](ssc, kafkaParams, topics)
    }
    kafkaStream
  }
}
