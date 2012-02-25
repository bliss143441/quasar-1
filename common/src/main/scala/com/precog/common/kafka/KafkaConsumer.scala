/*
 *  ____    ____    _____    ____    ___     ____ 
 * |  _ \  |  _ \  | ____|  / ___|  / _/    / ___|        Precog (R)
 * | |_) | | |_) | |  _|   | |     | |  /| | |  _         Advanced Analytics Engine for NoSQL Data
 * |  __/  |  _ <  | |___  | |___  |/ _| | | |_| |        Copyright (C) 2010 - 2013 SlamData, Inc.
 * |_|     |_| \_\ |_____|  \____|   /__/   \____|        All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the 
 * GNU Affero General Public License as published by the Free Software Foundation, either version 
 * 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; 
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See 
 * the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this 
 * program. If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.precog
package common 
package kafka

import scala.annotation.tailrec

import akka.dispatch.Future
import akka.dispatch.MessageDispatcher

import com.weiglewilczek.slf4s._ 

import _root_.kafka.api._
import _root_.kafka.consumer._
import _root_.kafka.message._

class KafkaBatchIngester(consumer: KafkaBatchConsumer)(processor: List[MessageAndOffset] => Unit)(implicit dispatch: MessageDispatcher) extends Logging {
  
  private val bufferSize = 1024 * 1024
  
  def start(nextOffset: => Long) = Future[Unit] {
    val consumerThread = new Thread() {
      val retryDelay = 5000

      override def run() {
        while(true) {
          val offset = nextOffset
          logger.debug("Kafka consumer starting from offset: " + offset)
          try {
            ingestBatch(offset, 0, 0, 0)
          } catch {
            case ex => 
              logger.error("Error in kafka consumer.", ex)
          }
          Thread.sleep(retryDelay)
        }
      }

      @tailrec
      def ingestBatch(offset: Long, batch: Long, delay: Long, waitCount: Long) {
        if(batch % 100 == 0) logger.debug("Processing kafka consumer batch %d [%s]".format(batch, if(waitCount > 0) "IDLE" else "ACTIVE"))
        
        // A future optimizatin would be to move this to another thread (or maybe actors)
        val messages = consumer.ingestBatch(offset, bufferSize) 

        val sizeInBytes = messages.map { _.message.size } reduce { _ + _ }

        if(messages.size > 0) {
          processor(messages)
        }

        val newDelay = delayStrategy(sizeInBytes, delay, waitCount)

        val (newOffset, newWaitCount) = if(messages.size > 0) {
          val o: Long = messages.last.offset
          logger.debug("Kafka consumer batch size: %d offset: %d)".format(messages.size, o))
          (o, 0L)
        } else {
          (offset, waitCount + 1)
        }
        
        Thread.sleep(newDelay)
        
        ingestBatch(newOffset, batch + 1, newDelay, newWaitCount)
      }
    }
    consumerThread.start()
  }

  def stop() = Future { consumer.close }
  
  val maxDelay = 100.0
  val waitCountFactor = 25

  def delayStrategy(messageBytes: Int, currentDelay: Long, waitCount: Long): Long = {
    if(messageBytes == 0) {
      val boundedWaitCount = if(waitCount > waitCountFactor) waitCountFactor else waitCount
      (maxDelay * boundedWaitCount / waitCountFactor).toLong
    } else {
      (maxDelay * (1.0 - messageBytes.toDouble / bufferSize)).toLong
    }
  }

}

class KafkaBatchConsumer(host: String, port: Int, topic: String) {
 
  private val timeout = 5000
  private val buffer = 64 * 1024

  private lazy val consumer = new SimpleConsumer(host, port, timeout, buffer) 

  def ingestBatch(offset: Long, bufferSize: Int): List[MessageAndOffset] = {
    val fetchRequest = new FetchRequest(topic, 0, offset, bufferSize)

    consumer.fetch(fetchRequest).toList
  }

  def close() {
    consumer.close
  }
}
