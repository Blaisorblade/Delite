package ppl.dsl.optiml.io

import java.io._
import scala.virtualization.lms.common.Base
import ppl.dsl.optiml.datastruct.scala._
import ppl.dsl.optiml.{OptiMLCompiler, OptiMLLift, OptiML}

trait MLInputReaderImplOps { this: Base =>
  def mlinput_read_impl(filename: Rep[String]) : Rep[Matrix[Double]]
  def mlinput_read_vector_impl(filename : Rep[String]) : Rep[Vector[Double]]
  def mlinput_read_grayscale_image_impl(filename: Rep[String]): Rep[GrayscaleImage]
  def mlinput_read_tokenmatrix_impl(filename: Rep[String]): Rep[TrainingSet[Double,Double]]
  def mlinput_read_template_models_impl(directory: Rep[String]): Rep[Vector[(String, Vector[BinarizedGradientTemplate])]]
}

trait MLInputReaderImplOpsStandard extends MLInputReaderImplOps {
  this: OptiMLCompiler with OptiMLLift =>
  
  ///////////////
  // kernels

  def mlinput_read_impl(filename: Rep[String]) = {
    val xfs = BufferedReader(FileReader(filename))
    var line = xfs.readLine()
    line = line.trim()
    // TODO: weirdness with StringOps, losing a \        
    var dbls = line.split("\\\\s+")
    val x = Matrix[Double](0, dbls.length)

    while (line != null){
      val v = Vector[Double](dbls.length, true)
      for (i <- 0 until dbls.length){
        v(i) = Double.parseDouble(dbls(i))
      }
      x += v

      line = xfs.readLine()
      if (line != null) {
        line = line.trim()
        dbls = line.split("\\\\s+")
      }
    }
    xfs.close()

    x
  }

  def mlinput_read_vector_impl(filename: Rep[String]) = {
    val x = Vector[Double](0, true)

    val xfs = BufferedReader(FileReader(filename))
    var line = xfs.readLine()
    while (line != null){
      line = line.trim()
      val dbl = Double.parseDouble(line)
      x += dbl

      line = xfs.readLine()
    }
    xfs.close()

    x
  }

  def mlinput_read_grayscale_image_impl(filename: Rep[String]): Rep[GrayscaleImage] = {
    val xfs = BufferedReader(FileReader(filename))
    var line = xfs.readLine()
    line = line.trim()
    var ints = line.split("\\\\s+")
    val x = Matrix[Int](0, ints.length)

    while (line != null) {
      val v = Vector[Int](ints.length, true)
      var i = unit(0)
      while (i < ints.length) {
        v(i) = Integer.parseInt(ints(i))
        i += 1
      }
      x += v

      line = xfs.readLine()
      if (line != null) {
        line = line.trim()
        ints = line.split("\\\\s+")
      }
    }
    xfs.close()

    GrayscaleImage(x)
  }


 /* the input file is expected to follow the format:
  *  <header>
  *  <num documents> <num tokens>
  *  <tokenlist>
  *  <document word matrix, where each row repesents a document and each column a distinct token>
  *    each line of the doc word matrix begins with class (0 or 1) and ends with -1
  *    the matrix is sparse, so each row has a tuple of (tokenIndex, number of appearances)
  */
  def mlinput_read_tokenmatrix_impl(filename: Rep[String]): Rep[TrainingSet[Double,Double]] = {

    var xs = BufferedReader(FileReader(filename))

    // header and metadata
    var header = xs.readLine()

    var line = xs.readLine()
    val counts = line.trim().split("\\\\s+")
    val numDocs = Integer.parseInt(counts(0))
    val numTokens = Integer.parseInt(counts(1))
    if ((numDocs < 0) || (numTokens < 0)) {
      error("Illegal input to readTokenMatrix")
    }

    // tokens
    val tokenlist = xs.readLine()

    val trainCatSeq = Vector[Double]()
    for (m <- 0 until numDocs){
      line = xs.readLine()
      line = line.trim()
      val nums = line.split("\\\\s+")

      trainCatSeq += Double.parseDouble(nums(0))
    }
    val trainCategory = trainCatSeq.t

    xs.close()
    xs = BufferedReader(FileReader(filename))
    xs.readLine(); xs.readLine(); xs.readLine()

    val trainMatSeq = Vector[Vector[Double]](0, true)
    for (m <- 0 until numDocs) {
      line = xs.readLine()
      line = line.trim()
      val nums = line.split("\\\\s+")

      val row = Vector[Double](numTokens,true)
      var cumsum = unit(0); var j = unit(1)
      // this could be vectorized
      while (j < nums.length - 1){
        cumsum += Integer.parseInt(nums(j))
        row(cumsum) = Double.parseDouble(nums(j+1))
        j += 2
      }
      trainMatSeq += row
    }
    val trainMatrix = Matrix(trainMatSeq)

    xs.close()

    //return (trainMatrix,tokenlist,trainCategory)
    TrainingSet[Double,Double](trainMatrix, Labels(trainCategory))
  }

  def mlinput_read_template_models_impl(directory: Rep[String]): Rep[Vector[(String, Vector[BinarizedGradientTemplate])]] = {
    val templateFiles = Vector[String](0, true)
    for (f <- File(directory).getCanonicalFile.listFiles) {
      templateFiles += f.getPath()
    }

    templateFiles.map { filename =>
      println("Loading model: " + filename)
      val templates = Vector[BinarizedGradientTemplate](0, true)

      val file = BufferedReader(FileReader(filename))

      if (file.readLine() != "bigg_object:") error("Illegal data format")
      file.readLine() //"============"
      val params = file.readLine().trim.split(" ")
      if (params(0) != "obj_name/obj_num/num_objs:") error("Illegal data format")
      val objName = params(1)
      val objId = params(2)
      val numObjs = Integer.parseInt(params(3))
      var i = unit(0)
      while (i < numObjs) {
        templates += loadModel(file)
        i += 1
      }
      (objName, templates)
    }
  }

  private def loadModel(file: Rep[BufferedReader]): Rep[BinarizedGradientTemplate] = {
    if (file.readLine().trim != "====OneBiGG====:") error("Illegal data format")
    var temp = file.readLine().trim.split(" ")
    if (temp(0) != "view/radius/reduction:") error("Illegal data format")
    val view = Integer.parseInt(temp(1))
    val radius = Integer.parseInt(temp(2))
    val reductionFactor = Integer.parseInt(temp(3))

    temp = file.readLine().trim.split(" ")
    if (temp(0) != "Gradients:") error("Illegal data format")
    val gradientsSize = Integer.parseInt(temp(1))
    val gradients = Vector[Int](gradientsSize,true)
    val gradientsString = file.readLine().trim.split(" ")
    var i = unit(0)
    while (i < gradientsSize) {
      gradients(i) = Integer.parseInt(gradientsString(i))
      i += 1
    }

    temp = file.readLine().trim.split(" ")
    if (temp(0) != "Match_list:") error("Illegal data format")
    val matchListSize = Integer.parseInt(temp(1))
    val matchList = IndexVector(0)
    val matchListString = file.readLine().trim.split(" ")
    i = 0
    while (i < matchListSize) {
      matchList += Integer.parseInt(matchListString(i))
      i += 1
    }

    temp = file.readLine().trim.split(" ")
    if (temp(0) != "Occlusions:") error("Illegal data format")
    val occlusionsSize = Integer.parseInt(temp(1))
    val occlusions = Vector[Vector[Int]]()
    val occlusionsString = file.readLine().trim.split(" ")
    if (occlusionsSize != 0) error("Occlusions not supported.")

    if (file.readLine().trim != "BoundingBox:") error("Illegal data format")
    val bbString = file.readLine().trim.split(" ")
    val x = Integer.parseInt(bbString(0))
    val y = Integer.parseInt(bbString(1))
    val width = Integer.parseInt(bbString(2))
    val height = Integer.parseInt(bbString(3))
    val bb = Rect(x, y, width, height)

    // TODO: Anand, should not be initializing these null unless we add setters to BinarizedGradientTemplate
    BinarizedGradientTemplate(radius, bb, null, 0, gradients, matchList, occlusions, null, null)
  }

}
