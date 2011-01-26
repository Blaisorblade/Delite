package ppl.dsl.optiml.datastruct.scala


class Rect(
  val x: Int,
  val y: Int,
  val width: Int,
  val height: Int
)

class BinarizedGradientTemplate (
  // In the reduced image. The side of the template square is then 2*r+1.
  val radius: Int,

  // Holds a tighter bounding box of the object in the original image scale
  val rect: Rect,
  val mask_list: Vector[Int],

  // Pyramid level of the template (reduction_factor = 2^level)
  val level: Int,

  // The list of gradients in the template
  val binary_gradients: Vector[Int],

  // indices to use for matching (skips zeros inside binary_gradients)
  val match_list: IndexVector,

  // This is a match list of list of sub-parts. Currently unused.
  val occlusions: Vector[Vector[Int]],

  val templates: Vector[BinarizedGradientTemplate],

  val hist: Vector[Float]
)

class BiGGDetection(
  val name: String,
  val score: Float,
  val roi: Rect,
  val mask: GrayscaleImage,
  val index: Int,
  val x: Int,
  val y: Int,
  val tpl: BinarizedGradientTemplate,
  val crt_tpl: BinarizedGradientTemplate
)

class BinarizedGradientPyramid(
  val gradientImage: GrayscaleImage,
  val pyramid: Vector[GrayscaleImage],
  val start_level: Int,
  val levels: Int,
  val fixedLevelIndex: Int
)


