package ppl.dsl.optiml

import virtualization.lms.internal.{Hosts, Expressions, CppHostTransfer}

trait OptiMLCppHostTransfer extends CppHostTransfer {

  val IR: Expressions
  import IR._

  override def emitSend(sym: Sym[Any], host: Hosts.Value): (String,String) = {
    if (host == Hosts.JVM) {
        remap(sym.tp) match {
          case "IndexVectorDense" =>
            val out = new StringBuilder
            val typeArg = Manifest.Int
            val signature = "jobject sendCPPtoJVM_%s(JNIEnv *env, %s *%s)".format(quote(sym),remap(sym.tp),quote(sym))
            out.append(signature + " {\n")
            out.append("\tjclass cls = env->FindClass(\"generated/scala/IndexVectorDense\");\n")
            out.append("\tjmethodID mid = env->GetMethodID(cls,\"<init>\",\"(IZ)V\");\n")
            out.append("\tjobject obj = env->NewObject(cls,mid,%s->length,%s->isRow);\n".format(quote(sym),quote(sym)))
            out.append("\tjmethodID mid_data = env->GetMethodID(cls,\"_data\",\"()[%s\");\n".format(JNITypeDescriptor(typeArg)))
            out.append("\t%sArray data = (%sArray)(env->CallObjectMethod(obj,mid_data));\n".format(JNIType(typeArg),JNIType(typeArg)))
            out.append("\t%s *dataPtr = (%s *)env->GetPrimitiveArrayCritical(data,0);\n".format(JNIType(typeArg),JNIType(typeArg)))
            out.append("\tmemcpy(dataPtr, %s->data, %s->length*sizeof(%s));\n".format(quote(sym),quote(sym),remap(typeArg)))
            out.append("\tenv->ReleasePrimitiveArrayCritical(data, dataPtr, 0);\n")
            out.append("\tenv->DeleteLocalRef(data);\n")
            out.append("\tenv->DeleteLocalRef(cls);\n")
            out.append("\treturn obj;\n")
            out.append("}\n")
            (signature+";\n", out.toString)
          case "IndexVectorRange" =>
            val out = new StringBuilder
            val typeArg = Manifest.Int
            val signature = "jobject sendCPPtoJVM_%s(JNIEnv *env, %s *%s)".format(quote(sym),remap(sym.tp),quote(sym))
            out.append(signature + " {\n")
            out.append("assert(false);\n")
            out.append("}\n")
            (signature+";\n", out.toString)  
          case _ => super.emitSend(sym, host)
        }
    }
    else
      super.emitSend(sym, host)
  }


  override def emitRecv(sym: Sym[Any], host: Hosts.Value): (String,String) = {
    if (host == Hosts.JVM) {
        remap(sym.tp) match {
          case "IndexVectorDense" =>
            val out = new StringBuilder
            val typeArg = Manifest.Int
            val signature = "%s *recvCPPfromJVM_%s(JNIEnv *env, jobject obj)".format(remap(sym.tp),quote(sym))
            out.append(signature + " {\n")
            out.append("\tjclass cls = env->GetObjectClass(obj);\n")
            out.append("\tjmethodID mid_length = env->GetMethodID(cls,\"_length\",\"()I\");\n")
            out.append("\tjmethodID mid_isRow = env->GetMethodID(cls,\"_isRow\",\"()Z\");\n")
            out.append("\t%s *%s = new %s(env->CallIntMethod(obj,mid_length),env->CallBooleanMethod(obj,mid_isRow));\n".format(remap(sym.tp),quote(sym),remap(sym.tp)))
            out.append("\tjmethodID mid_data = env->GetMethodID(cls,\"_data\",\"()[%s\");\n".format(JNITypeDescriptor(typeArg)))
            out.append("\t%sArray data = (%sArray)(env->CallObjectMethod(obj,mid_data));\n".format(JNIType(typeArg),JNIType(typeArg)))
            out.append("\t%s *dataPtr = (%s *)env->GetPrimitiveArrayCritical(data,0);\n".format(JNIType(typeArg),JNIType(typeArg)))
            out.append("\tmemcpy(%s->data, dataPtr, %s->length*sizeof(%s));\n".format(quote(sym),quote(sym),remap(typeArg)))
            out.append("\tenv->ReleasePrimitiveArrayCritical(data, dataPtr, 0);\n")
            out.append("\tenv->DeleteLocalRef(data);\n")
            out.append("\tenv->DeleteLocalRef(cls);\n")
            out.append("\treturn %s;\n".format(quote(sym)))
            out.append("}\n")
            (signature+";\n", out.toString)
          case "IndexVectorRange" =>
            val out = new StringBuilder
            val typeArg = Manifest.Int
            val signature = "%s *recvCPPfromJVM_%s(JNIEnv *env, jobject obj)".format(remap(sym.tp),quote(sym))
            out.append(signature + " {\n")
            out.append("assert(false);\n")
            out.append("}\n")
            (signature+";\n", out.toString)  
          case _ => super.emitRecv(sym, host)
        }
    }
    else
      super.emitRecv(sym, host)
  }

  override def emitSendView(sym: Sym[Any], host: Hosts.Value): (String,String) = {
    if (host == Hosts.JVM) {
        remap(sym.tp) match {
          case "IndexVectorDense" | "IndexVectorRange" =>
            val out = new StringBuilder
            val signature = "jobject sendViewCPPtoJVM_%s(JNIEnv *env, %s *%s)".format(quote(sym),remap(sym.tp),quote(sym))
            out.append(signature + " {\n")
            out.append("\tassert(false);\n")
            out.append("}\n")
            (signature+";\n", out.toString)
          case _ => super.emitSendView(sym, host)
        }
    }
    else
      super.emitSendView(sym, host)
  }


  override def emitRecvView(sym: Sym[Any], host: Hosts.Value): (String,String) = {
    if (host == Hosts.JVM) {
        remap(sym.tp) match {
          case "IndexVectorDense" =>
            val out = new StringBuilder
            val typeArg = Manifest.Int
            val signature = "%s *recvViewCPPfromJVM_%s(JNIEnv *env, jobject obj)".format(remap(sym.tp),quote(sym))
            out.append(signature + " {\n")
            out.append("\tjclass cls = env->GetObjectClass(obj);\n")
            out.append("\tjmethodID mid_length = env->GetMethodID(cls,\"_length\",\"()I\");\n")
            out.append("\tjmethodID mid_isRow = env->GetMethodID(cls,\"_isRow\",\"()Z\");\n")
            out.append("\tjmethodID mid_data = env->GetMethodID(cls,\"_data\",\"()[%s\");\n".format(JNITypeDescriptor(typeArg)))
            out.append("\t%sArray data = (%sArray)(env->CallObjectMethod(obj,mid_data));\n".format(JNIType(typeArg),JNIType(typeArg)))
            out.append("\t%s *dataPtr = (%s *)env->GetPrimitiveArrayCritical(data,0);\n".format(JNIType(typeArg),JNIType(typeArg)))
            out.append("\t%s *%s = new %s((%s *)dataPtr,env->CallIntMethod(obj,mid_length),env->CallBooleanMethod(obj,mid_isRow));\n".format(remap(sym.tp),quote(sym),remap(sym.tp),remap(typeArg)))
            out.append("\tenv->DeleteLocalRef(data);\n")   //TODO: This should not be done at this point?
            out.append("\tenv->DeleteLocalRef(cls);\n")
            out.append("\treturn %s;\n".format(quote(sym)))
            out.append("}\n")
            (signature+";\n", out.toString)
          case "IndexVectorRange" =>
            val out = new StringBuilder
            val typeArg = Manifest.Int
            val signature = "%s *recvViewCPPfromJVM_%s(JNIEnv *env, jobject obj)".format(remap(sym.tp),quote(sym))
            out.append(signature + " {\n")
            out.append("\tjclass cls = env->GetObjectClass(obj);\n")
            out.append("\tjmethodID mid_start = env->GetMethodID(cls,\"_start\",\"()I\");\n")
            out.append("\tjmethodID mid_end = env->GetMethodID(cls,\"_end\",\"()I\");\n")
            out.append("\tjmethodID mid_stride = env->GetMethodID(cls,\"_stride\",\"()I\");\n")
            out.append("\tjmethodID mid_isRow = env->GetMethodID(cls,\"_isRow\",\"()Z\");\n")
            out.append("\t%s *%s = new IndexVectorRange(env->CallIntMethod(obj,mid_start),env->CallIntMethod(obj,mid_end),env->CallIntMethod(obj,mid_stride),env->CallBooleanMethod(obj,mid_isRow));\n".format(remap(sym.tp),quote(sym)))
            out.append("\tenv->DeleteLocalRef(cls);\n")
            out.append("\treturn %s;\n".format(quote(sym)))
            out.append("}\n")
            (signature+";\n", out.toString) 
          case _ => super.emitRecvView(sym, host)
        }
    }
    else
      super.emitRecvView(sym, host)
  }

  override def emitSendUpdate(sym: Sym[Any], host: Hosts.Value): (String,String) = {
    if (host == Hosts.JVM) {
        remap(sym.tp) match {
          case "IndexVectorDense" | "IndexVectorRange" =>
            val out = new StringBuilder
            val typeArg = Manifest.Int
            val signature = "void sendUpdateCPPtoJVM_%s(JNIEnv *env, jobject obj, %s *%s)".format(quote(sym),remap(sym.tp),quote(sym))
            out.append(signature + " {\n")
            out.append("assert(false);\n")
            out.append("}\n")
            (signature+";\n", out.toString)
          case _ => super.emitSendUpdate(sym, host)
        }
    }
    else
      super.emitSendUpdate(sym, host)
  }

  override def emitRecvUpdate(sym: Sym[Any], host: Hosts.Value): (String,String) = {
    if (host == Hosts.JVM) {
        remap(sym.tp) match {
          case "IndexVectorDense" | "IndexVectorRange" =>
            val out = new StringBuilder
            val typeArg = Manifest.Int
            val signature = "void recvUpdateCPPfromJVM_%s(JNIEnv *env, jobject obj, %s *%s)".format(quote(sym),remap(sym.tp),quote(sym))
            out.append(signature + " {\n")
            out.append("assert(false);\n")
            out.append("}\n")
            (signature+";\n", out.toString)
          case _ => super.emitRecvUpdate(sym, host)
        }
    }
    else
      super.emitRecvUpdate(sym, host)
  }

}
