// See LICENSE for license details.

package rocketchip

import Chisel._
import sys.process.stringSeqToProcess

abstract class RocketTestSuite {
  val dir: String
  val makeTargetName: String
  val names: Set[String]
  def postScript = s"""

$$(addprefix $$(output_dir)/, $$(addsuffix .hex, $$($makeTargetName))): $$(output_dir)/%.hex: $dir/%.hex
\tmkdir -p $$(output_dir)
\tln -fs $$< $$@

$$(addprefix $$(output_dir)/, $$($makeTargetName)): $$(output_dir)/%: $dir/%
\tmkdir -p $$(output_dir)
\tln -fs $$< $$@

run-$makeTargetName: $$(addprefix $$(output_dir)/, $$(addsuffix .out, $$($makeTargetName)))
\t@echo; perl -ne 'print "  [$$$$1] $$$$ARGV \\t$$$$2\\n" if /\\*{3}(.{8})\\*{3}(.*)/' $$^; echo;

run-$makeTargetName-debug: $$(addprefix $$(output_dir)/, $$(addsuffix .vpd, $$($makeTargetName)))
\t@echo; perl -ne 'print "  [$$$$1] $$$$ARGV \\t$$$$2\\n" if /\\*{3}(.{8})\\*{3}(.*)/' $$(patsubst %.vpd,%.out,$$^); echo;
"""
}

class AssemblyTestSuite(makePrefix: String, val toolsPrefix: String, val names: Set[String])(val envName: String) extends RocketTestSuite {
  val dir = "$(base_dir)/riscv-tools/riscv-tests/isa"
  val makeTargetName = makePrefix + "-" + envName + "-asm-tests"
  override def toString = s"$makeTargetName = \\\n" + names.map(n => s"\t$toolsPrefix-$envName-$n").mkString(" \\\n") + postScript
}

class BenchmarkTestSuite(makePrefix: String, val dir: String, val names: Set[String]) extends RocketTestSuite {
  val makeTargetName = makePrefix + "-bmark-tests"
  override def toString = s"$makeTargetName = \\\n" + names.map(n => s"\t$n.riscv").mkString(" \\\n") + postScript
}

object TestGeneration extends FileSystemUtilities{
  import scala.collection.mutable.HashMap
  val asmSuites = new HashMap[String,AssemblyTestSuite]()
  val bmarkSuites = new  HashMap[String,BenchmarkTestSuite]()

  def addSuite(s: RocketTestSuite) {
    s match {
      case a: AssemblyTestSuite => asmSuites += (a.makeTargetName -> a)
      case b: BenchmarkTestSuite => bmarkSuites += (b.makeTargetName -> b)
    }
  }
  
  def addSuites(s: Seq[RocketTestSuite]) { s.foreach(addSuite) }

  def generateMakefrag {
    def gen(kind: String, s: Seq[RocketTestSuite]) = {
      if(s.length > 0) {
        val targets = s.map(t => s"$$(${t.makeTargetName})").mkString(" ") 
        s.map(_.toString).mkString("\n") + s"""
run-$kind-tests: $$(addprefix $$(output_dir)/, $$(addsuffix .out, $targets))
\t@echo; perl -ne 'print "  [$$$$1] $$$$ARGV \\t$$$$2\\n" if /\\*{3}(.{8})\\*{3}(.*)/' $$^; echo;
run-$kind-tests-debug: $$(addprefix $$(output_dir)/, $$(addsuffix .vpd, $targets))
\t@echo; perl -ne 'print "  [$$$$1] $$$$ARGV \\t$$$$2\\n" if /\\*{3}(.{8})\\*{3}(.*)/' $$(patsubst %.vpd,%.out,$$^); echo;
run-$kind-tests-fast: $$(addprefix $$(output_dir)/, $$(addsuffix .run, $targets))
\t@echo; perl -ne 'print "  [$$$$1] $$$$ARGV \\t$$$$2\\n" if /\\*{3}(.{8})\\*{3}(.*)/' $$^; echo;
"""
      } else { "\n" }
    }

    val f = createOutputFile(s"${Driver.topComponent.get.name}.${Driver.chiselConfigClassName.get}.d")
    f.write(List(gen("asm", asmSuites.values.toSeq), gen("bmark", bmarkSuites.values.toSeq)).mkString("\n"))
    f.close
  }
}

object DefaultTestSuites {
  val rv32uiNames = Set(
    "simple", "add", "addi", "and", "andi", "auipc", "beq", "bge", "bgeu", "blt", "bltu", "bne", "fence_i", 
    "j", "jal", "jalr", "lb", "lbu", "lh", "lhu", "lui", "lw", "or", "ori", "sb", "sh", "sw", "sll", "slli",
    "slt", "slti", "sra", "srai", "srl", "srli", "sub", "xor", "xori")
  val rv32ui = new AssemblyTestSuite("rv32ui", "rv32ui", rv32uiNames)(_)

  val rv32umNames = Set("mul", "mulh", "mulhsu", "mulhu", "div", "divu", "rem", "remu")
  val rv32um = new AssemblyTestSuite("rv32um", "rv32ui", rv32umNames)(_)

  val rv32uaNames = Set("amoadd_w", "amoand_w", "amoor_w", "amoxor_w", "amoswap_w", "amomax_w", "amomaxu_w", "amomin_w", "amominu_w")
  val rv32ua = new AssemblyTestSuite("rv32ua", "rv32ui", rv32uaNames)(_)

  val rv64uiNames = Set("addw", "addiw", "ld", "lwu", "sd", "slliw", "sllw", "sltiu", "sltu", "sraiw", "sraw", "srliw", "srlw", "subw")
  val rv64ui = new AssemblyTestSuite("rv64ui", "rv64ui", rv32uiNames ++ rv64uiNames)(_)

  val rv64umNames = Set("divuw", "divw", "mulw", "remuw", "remw")
  val rv64um = new AssemblyTestSuite("rv64um", "rv64ui", rv32umNames ++ rv64umNames)(_)

  val rv64uaNames = rv32uaNames.map(_.replaceAll("_w","_d"))
  val rv64ua = new AssemblyTestSuite("rv64ua", "rv64ui", rv32uaNames ++ rv64uaNames)(_)

  val rv64ufNames = Set("ldst", "move", "fsgnj", "fcmp", "fcvt", "fcvt_w", "fclass", "fadd", "fdiv", "fmin", "fmadd", "structural")
  val rv64uf = new AssemblyTestSuite("rv64uf", "rv64uf", rv64ufNames)(_)
  val rv64ufNoDiv = new AssemblyTestSuite("rv64uf", "rv64uf", rv64ufNames - "fdiv")(_)

  val rv64siNames = Set("csr", "illegal", "ma_fetch", "ma_addr", "scall", "sbreak", "wfi")
  val rv64si = new AssemblyTestSuite("rv64si", "rv64si", rv64siNames)(_)

  val rv64miNames = Set("csr", "mcsr", "wfi", "dirty", "illegal", "ma_addr", "ma_fetch", "sbreak", "scall", "timer")
  val rv64mi = new AssemblyTestSuite("rv64mi", "rv64mi", rv64miNames)(_)

  // TODO: "rv64ui-pm-lrsc", "rv64mi-pm-ipi",

  val rv64u = List(rv64ui, rv64um, rv64ua)
  val rv64i = List(rv64ui, rv64si, rv64mi)

  val bmarks = new BenchmarkTestSuite("basic", "$(base_dir)/riscv-tools/riscv-tests/benchmarks", Set(
    "median", "multiply", /*"qsort",*/ "towers", "vvadd", /*"mm", "dhrystone",*/ "spmv"/*, "mt-vvadd", "mt-matmul"*/))

  val mtBmarks = new BenchmarkTestSuite("mt", "$(base_dir)/riscv-tools/riscv-tests/mt",
    ((0 to 4).map("vvadd"+_) ++ 
    List("ad","ae","af","ag","ai","ak","al","am","an","ap","aq","ar","at","av","ay","az",
         "bb","bc","bf","bh","bj","bk","bm","bo","br","bs","ce","cf","cg","ci","ck","cl",
         "cm","cs","cv","cy","dc","df","dm","do","dr","ds","du","dv").map(_+"_matmul")).toSet)

  val zscaleBmarks = new BenchmarkTestSuite("zscale", "$(base_dir)/zscale/sw", Set(
    "led", "mbist"))
}

class RocketChipReplay(c: Module, samples: Seq[strober.Sample], matchFile: Option[String] = None,
    testCmd: Option[String] = Driver.testCommand, log: Option[String] = None)
    extends strober.Replay(c.asInstanceOf[Top], samples, matchFile, testCmd, None, log) {
  override def expect(data: Bits, expected: BigInt) = {
    // Sadly, Design Compiler optimization prunes the registers
    // directly connected to the tag output, causing output value descrepancy...
    // Thus, check only when the memory request is valid
    val top = c.asInstanceOf[Top]
    if (data eq top.io.mem.req_cmd.bits.tag)
      peek(top.io.mem.req_cmd.valid) == 0 || super.expect(data, expected)
    else
      super.expect(data, expected)
  }
}

object TestGenerator extends App {
  val gen = () => Class.forName("rocketchip."+args(0)).newInstance().asInstanceOf[Module]
  if (args(1) == "replay") {
    import scala.actors.Actor._
    // args(2): sample file
    // args(3): match file
    // args(4): # of replay instances in parallel
    val top = chiselMain.run(args drop 4, gen)
    val logDir = Driver.ensureDir(s"${Driver.targetDir}/logs")
    val prefix = (new java.io.File(args(2)).getName split '.').head
    val samples = strober.Sample.load(args(2), 
      new java.io.PrintStream(s"${logDir}/${prefix}-sample.log"))
    val matchFile = args(3) match { case "none" => None case p => Some(p) }
    val N = args(4).toInt
    case object ReplayFin
    val replays = List.fill(N){ actor { loop { react {
      case (sample: strober.Sample, cmd: Option[String], log: Option[String]) =>
        assert((new RocketChipReplay(top, Seq(sample), matchFile, cmd, log=log)).finish)
      case ReplayFin => exit()
    } } } }
    samples.zipWithIndex foreach {case (sample, idx) =>
      val vcd  = s"${Driver.targetDir}/${prefix}_${idx}_pipe.vcd"
      val vpd  = s"${Driver.targetDir}/${prefix}_${idx}.vpd"
      val saif = s"${Driver.targetDir}/${prefix}_${idx}.saif"
      val log  = s"${logDir}/${prefix}_${idx}.log"
      val cmd  = Driver.testCommand match {
        case None => 
        case Some(p) if matchFile == None =>
          Some(List(p, s"+vpdfile=${vpd}") mkString " ")
        case Some(p) =>
          Seq("rm", "-rf", vcd, vpd).!
          val pipe = List(p, s"+vpdfile=${vpd}", s"+vcdfile=${vcd}") mkString " "
          Some(List("vcd2saif", "-input", vcd, "-output", saif, "-pipe", s""""${pipe}" """) mkString " ")
      }
      replays(idx % N) ! (sample, cmd, Some(log))
    }
    replays foreach (_ ! ReplayFin)
  } else {
    chiselMain.run(args.drop(1), gen) 
    TestGeneration.generateMakefrag
  }
}
