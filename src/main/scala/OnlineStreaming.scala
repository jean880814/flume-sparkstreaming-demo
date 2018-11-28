import kafka.api.OffsetRequest
import kafka.common.TopicAndPartition
import kafka.utils.{ZKGroupTopicDirs, ZkUtils}
import org.I0Itec.zkclient.ZkClient
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.rdd.RDD
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.kafka.{HasOffsetRanges, OffsetRange}
import org.apache.spark.{HashPartitioner, SparkConf, SparkContext}
import org.apache.spark.streaming.{Seconds, StreamingContext}
import redis.clients.jedis.Jedis

object OnlineStreaming {
  def main(args: Array[String]): Unit = {
    System.setProperty("hadoop.home.dir", "D:\\tools\\hadoop-common-2.6.0-bin-master")
    val Array(brokers, topics, ipRules, zkPath, group, checkpoint, redisHost, redisPort) = args
    val conifg = new SparkConf().setAppName("OnlineStreaming").setMaster("local[*]")
    conifg.set("spark.streaming.backpressure.enabled", "true")
    val ssc = new StreamingContext(conifg, Seconds(5))
    ssc.checkpoint(checkpoint)
    val sc = new SparkContext(conifg)
    val kafkaParams = Map("bootstrap.servers" -> brokers, "group.id" -> group, "auto.offset.reset" -> OffsetRequest.SmallestTimeString);
    val zkClient = new ZkClient(zkPath) with Serializable
    //ip规则路径并广播变量
    val rules: RDD[String] = sc.textFile(ipRules)
    val rulesFormat: RDD[(Long, Long, String)] = rules.map(lines => {
      val fields = lines.split("[|]")
      (fields(2).toLong, fields(3).toLong, fields(6))
    })
    val allRules = rulesFormat.collect()
    val broadcast: Broadcast[Array[(Long, Long, String)]] = sc.broadcast(allRules)
    val topicSet = topics.split(",").toSet

    for (topic <- topicSet) {
      val topPicDirs = new ZKGroupTopicDirs(group, topic) with Serializable
      val zkTopicPath = s"${topPicDirs.consumerOffsetDir}"
      val messages = KafkaOffsetManager.readOffsetsAndCreateDStream(ssc, kafkaParams, zkClient, group, zkTopicPath, topic)
      var offsetsRanges: Array[OffsetRange] = Array[OffsetRange]()
      val rememberOffset: DStream[(String, String)] = messages.transform(rdd => {
        offsetsRanges = rdd.asInstanceOf[HasOffsetRanges].offsetRanges
        rdd
      })
      rememberOffset.map(t => {
        val msg = t._2
        val fields = msg.split(" ")
        val ip = fields(3)
        val ipNum = IpUtil.ipToLong(ip)
        val rules: Array[(Long, Long, String)] = broadcast.value
        val index: Int = IpUtil.binarySearch(rules, ipNum)
        val province = rules(index)._3
        (province, 1)
      }).updateStateByKey(updateFunc, new HashPartitioner(ssc.sparkContext.defaultParallelism), true).foreachRDD(
        rdd => {
          rdd.foreachPartition(partition => {
            partition.foreach(msg=>{
              val jedis: Jedis = JedisConnetionPool.getConnection(redisHost, redisPort.toInt)
              jedis.set(msg._1, msg._2.toString)
            })
          })
          for(o <- offsetsRanges){
            val zkOffsetPath = s"$zkTopicPath/${o.partition}"
            ZkUtils.updatePersistentPath(zkClient, zkOffsetPath, o.fromOffset.toString)
          }
        }
      )
    }

  }


  val updateFunc = (it: Iterator[(String, Seq[Int], Option[Int])]) => {
    it.map(t => (t._1, t._2.sum + t._3.getOrElse(0)))
  }
}
