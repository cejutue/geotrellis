package geotrellis.raster.reproject

import geotrellis.raster._
import geotrellis.vector._
import geotrellis.vector.reproject._
import geotrellis.testkit._
import geotrellis.proj4._

import org.scalacheck._
import Prop._
import Gen._
import Arbitrary._

import spire.syntax.cfor._

object RowTransformCheck extends Properties("RowTransform") {
  case class TestCase(extent: Extent, srcX: Array[Double], srcY: Array[Double])

  def genPoint(xmin: Double, ymin: Double, 
               xmax: Double, ymax: Double): Gen[Point] =
    for {
      x <- choose(-180,179.9999)
      y <- choose(-90,89.9999)
    } yield Point(x, y)

  lazy val genExtent: Gen[Extent] = 
    for {
      p1 <- genPoint(-180, -90, 179.9999, 89.9999)
      p2 <- genPoint(-180, -90, 179.9999, 89.9999)
    } yield {
        val (x1, y1) = (p1.x, p1.y)
      val (x2, y2) = (p2.x, p2.y)

      val (xmin, xmax) = 
        if(x1 < x2) (x1, x2) else (x2, x1)

      val (ymin, ymax) =
        if(y1 < y2) (y1, y2) else (y2, y1)

      Extent(xmin, ymin, xmax, ymax)
    }

  lazy val genTestCase: Gen[TestCase] =
    for {
      extent <- genExtent
      size <- Gen.choose(50,100)
      points <- Gen.containerOfN[Seq,Point](size,genPoint(extent.xmin, extent.ymin, extent.xmax, extent.ymax))
    } yield {
      TestCase(extent, points.map(_.x).toArray, points.map(_.y).toArray)
    }

  implicit lazy val arbTestCase: Arbitrary[TestCase] =
    Arbitrary(genTestCase)


  case class Threshold(v: Double)
  lazy val genThreshold: Gen[Threshold] = 
    for {
      v <- choose(0.0, 5.0)
    } yield Threshold(v)

  implicit lazy val arbThreshold: Arbitrary[Threshold] =
    Arbitrary(genThreshold)

  val transform = Transform(LatLng, WebMercator)

  property("Stays within thresholds") = forAll { (testCase: TestCase, thresh: Threshold) =>
    val TestCase(extent, srcX, srcY) = testCase
    val threshold = thresh.v
    val destX = Array.ofDim[Double](srcX.size)
    val destY = destX.clone

    val rowTransform = RowTransform.approximate(transform, threshold)
    rowTransform(srcX, srcY, destX, destY)

    val src =  srcX.zip(srcY).map { case (x, y) => Point(x, y) }
    val srcProjected = src.map(_.reproject(transform))
    val dest = destX.zip(destY).map { case (x, y) => Point(x, y) }

    srcProjected.zip(dest)
       .map { case(p1, p2) => 
         val dx = p1.x - p2.x
         val dy = p1.y - p2.y
         math.sqrt(dx*dx + dy*dy) < threshold
       }
      .foldLeft(true)(_ && _)
  }
}
