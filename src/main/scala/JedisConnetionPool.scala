import redis.clients.jedis.{Jedis, JedisPool, JedisPoolConfig}

object JedisConnetionPool {
  def getConnection(host: String, port: Int): Jedis = {
    val config = new JedisPoolConfig()
    config.setMaxTotal(10)
    config.setMaxIdle(5)
    config.setTestOnBorrow(true)
    val pool = new JedisPool(config, host, port)
    pool.getResource
  }
}
