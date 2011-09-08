package ppl.delite.framework.extern.lib

object clBLAS extends ExternalLibrary {
  val libName = "clBLAS"
  val configFile = "clBLAS.xml"
  val ext = "cpp"
  //val compileFlags = List( "-w", "-lOpenCL", "-lclblas", "-O3", "-shared", "-fPIC")
  val compileFlags = List( "-w", "-O3", "-shared", "-fPIC")
  val outputSwitch = "-o"
  
  override val header = """
#include <stdlib.h>
#include <stdio.h>
#include <limits>
#include <CL/cl.h>
#include "clblas.h"
"""
}
