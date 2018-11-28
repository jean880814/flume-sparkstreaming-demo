import kafka.api.OffsetRequest
import kafka.common.TopicAndPartition
import kafka.consumer.ConsumerConfig
import kafka.message.MessageAndMetadata
import kafka.serializer.StringDecoder
import kafka.utils.{ZKGroupTopicDirs, ZkUtils}
import org.I0Itec.zkclient.ZkClient
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.streaming.dstream.{DStream, InputDStream}
import org.apache.spark.{HashPartitioner, SparkConf, SparkContext}
import org.apache.spark.streaming.kafka.{HasOffsetRanges, KafkaUtils, OffsetRange}
import org.apache.spark.streaming.{Seconds, StreamingContext}

object FlumeStreamingTest {


  def main(args: Array[String]) {

    System.setProperty("hadoop.home.dir", "D:\\tools\\hadoop-common-2.6.0-bin-master")
    val Array(brokers, topics) = args

    val group = "g002"
    val sparkConf = new SparkConf().setAppName("FlumeStreamingTest").setMaster("local[*]")
    sparkConf.set("spark.streaming.backpressure.enabled","true")
    val ssc = new StreamingContext(sparkConf, Seconds(2))
    ssc.checkpoint("./ck")
    val kafkaParams = Map[String, String]("bootstrap.servers" -> brokers, "group.id" -> group, "auto.offset.reset" -> OffsetRequest.SmallestTimeString)
    val zkClient = new ZkClient("hadoop.lj04:2181") with Serializable

    val topicsSet = topics.split(",").toSet
    for (topic <- topicsSet){
      val topPicDirs = new ZKGroupTopicDirs(group, topic) with Serializable
      val zkTopicPath = s"${topPicDirs.consumerOffsetDir}"
      val messages = KafkaOffsetManager.readOffsetsAndCreateDStream(ssc, kafkaParams, zkClient, group, zkTopicPath, topic)
      var offsetsRanges = Array[OffsetRange]()
      val rememberOffset: DStream[(String, String)] = messages.transform (
        rdd => {
          offsetsRanges = rdd.asInstanceOf[HasOffsetRanges].offsetRanges
          rdd
        }
      )
      val reduces: DStream[(String, Int)] = rememberOffset.map(_._2).flatMap(_.split(" ")).map((_, 1))
      val updateReduces: DStream[(String, Int)] = reduces.updateStateByKey(updateFunc, new HashPartitioner(ssc.sparkContext.defaultParallelism), true)
      updateReduces.foreachRDD(rdd => {
          rdd.foreachPartition(partitions => {
            partitions.foreach(messages => {
              println(messages)
//              println(offsetsRanges.toBuffer)
            })
          })
          for (o <- offsetsRanges) {
            val zkOffsetPath = s"$zkTopicPath/${o.partition}"
            ZkUtils.updatePersistentPath(zkClient, zkOffsetPath, o.fromOffset.toString)
          }
      })
    }
    //    val words = lines.flatMap(_.split(" "))
    //    val wordCounts: DStream[(String, Int)] = words.map((_, 1))
    //    val result = wordCounts.updateStateByKey(updateFunc, new HashPartitioner(ssc.sparkContext.defaultParallelism), true)

    //    result.print()
    ssc.start()
    ssc.awaitTermination()
  }


  val updateFunc = (it: Iterator[(String, Seq[Int], Option[Int])]) => {
    it.map(t => (t._1, t._2.sum + t._3.getOrElse(0)))
  }

}
