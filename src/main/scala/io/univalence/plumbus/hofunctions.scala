package io.univalence.plumbus

import io.univalence.plumbus.internal.CleanFromRow
import org.apache.spark.sql._
import org.apache.spark.sql.functions._
import scala.reflect.runtime.universe.TypeTag

object hofunctions {

  object implicits {
    implicit class RicherColumn(column: Column) {

      def map[A: CleanFromRow: TypeTag, B: TypeTag](f: A => B): Column = {
        val f0 = udf[Seq[B], Seq[A]](serializeAndClean(s => s.map(f)))

        f0(column)
      }

      def filter[A: CleanFromRow: TypeTag](p: A => Boolean): Column = {
        val p0 = udf[Seq[A], Seq[A]](serializeAndClean(s => s.filter(p)))

        p0(column)
      }

      def flatMap[A: CleanFromRow : TypeTag, B : TypeTag](f: A => Seq[B]): Column = {
        val f0 = udf[Seq[B], Seq[A]](serializeAndClean(s => s.flatMap(f)))

        f0(column)
      }
    }
  }

  import com.twitter.chill.Externalizer

  def serializeAndCleanValue[A: CleanFromRow](a: A): A =
    if (a == null)
      null.asInstanceOf[A]
    else
      Externalizer(implicitly[CleanFromRow[A]].clean _).get(a)

  def serializeAndClean[A: CleanFromRow, B](f: Seq[A] => B): Seq[A] => B = {
    val cleaner: Externalizer[A => A] =
      Externalizer(implicitly[CleanFromRow[A]].clean _)
    val fExt: Externalizer[Seq[A] => B] =
      Externalizer(f)

    values =>
      if (values == null) {
        null.asInstanceOf[B]
      } else {
        val fExt0: Seq[A] => B = fExt.get
        val cleaner0: A => A   = cleaner.get

        fExt0(values.map(cleaner0))
      }
  }

}