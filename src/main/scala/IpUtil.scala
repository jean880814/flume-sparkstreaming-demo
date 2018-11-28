object IpUtil {
  def ipToLong(ip: String): Long = {
    val ipArray = ip.split("[.]")
    var ipNumber = 0l
    for(i <- 0 until ipArray.length) {
      ipNumber = ipArray(i).toLong | ipNumber << 8L
    }
    ipNumber
  }

  def  binarySearch(lines: Array[(Long, Long, String)], ip: Long) : Int = {
    var low = 0
    var high = lines.length - 1
    while (low <= high) {
      val middle = (low + high)/2
      if ((ip >= lines(middle)._1) && (ip <= lines(middle)._2))
        return middle
      if (ip < lines(middle)._1)
        high = middle - 1
      else
        low = middle + 1
    }
    -1
  }
}
