// Copyright (C) 2017 Calin Cruceru <calin.cruceru@stud.acs.upb.ro>.
//
// See the LICENCE file distributed with this work for additional
// information regarding copyright ownership.

package org.symnet
package models.iptables
package extensions.nat

import org.change.v2.analysis.expression.concrete.{ConstantValue, SymbolicValue}
import org.change.v2.analysis.expression.concrete.nonprimitive.:@
import org.change.v2.analysis.processingmodels.Instruction
import org.change.v2.analysis.processingmodels.instructions._
import org.change.v2.util.canonicalnames.{IPSrc, TcpSrc}

import types.net.{Ipv4, PortRange}

import core._
import extensions.filter.ProtocolMatch

case class SnatTarget(
    lowerIp:   Ipv4,
    upperIp:   Option[Ipv4],
    portRange: Option[PortRange]) extends Target {

  override protected def validateIf(
      rule: Rule,
      chain: Chain,
      table: Table): Boolean =
    // Check the table/chain in which this target is valid.
    table.name == "nat" && chain.name == "POSTROUTING" &&
    // Check that 'tcp' or 'udp' is specified when given the port range.
    //
    // The existance of the port range implies that '-p tcp/udp' must
    // have been specified.
    (portRange.isEmpty || ProtocolMatch.ruleMatchesTcpOrUdp(rule))

  // TODO: It is currently assumed that both TCP and UDP store the port address
  // at the same offset.
  override def seflCode(options: SeflGenOptions): Instruction = {
    // Get the name of the metadata tags.
    val fromIp = virtdev.snatFromIp(options.id)
    val fromPort = virtdev.snatFromPort(options.id)
    val toIp = virtdev.snatToIp(options.id)
    val toPort = virtdev.snatToPort(options.id)

    // If the upper bound is not given, we simply constrain on [lower, lower].
    val (lower, upper) = (lowerIp, upperIp getOrElse lowerIp)

    InstructionBlock(
      // Save original addresses.
      Assign(fromIp, :@(IPSrc)),
      Assign(fromPort, :@(TcpSrc)),

      // Mangle IP address.
      Assign(IPSrc, SymbolicValue()),
      Constrain(IPSrc, :&:(:>=:(ConstantValue(lower.host)),
                           :<=:(ConstantValue(upper.host)))),

      // Mangle TCP/UDP port address.
      Assign(TcpSrc, SymbolicValue()),
      if (portRange.isDefined) {
        // If a port range was specified, use it.
        val (lowerPort, upperPort) = portRange.get

        Constrain(TcpSrc, :&:(:>=:(ConstantValue(lowerPort)),
                              :<=:(ConstantValue(upperPort))))
      } else {
        // Otherwise (from docs):
        //
        //    If no port range is specified, then source ports below 512 will be
        //    mapped to other ports below 512: those between 512 and 1023
        //    inclusive will be mapped to ports below 1024, and other ports will
        //    be mapped to 1024 or above. Where possible, no port alteration
        //    will occur.
        If(Constrain(fromPort, :<:(ConstantValue(512))),
           // then
           Constrain(TcpSrc, :<:(ConstantValue(512))),
           // else
           If(Constrain(fromPort, :<:(ConstantValue(1024))),
              // then
              Constrain(TcpSrc, :&:(:>=:(ConstantValue(512)),
                                    :<:(ConstantValue(1024)))),
              // else
              Constrain(TcpSrc, :>=:(ConstantValue(1024)))))
      },

      // Save the new addresses.
      Assign(toIp, :@(IPSrc)),
      Assign(toPort, :@(TcpSrc)),

      // In the end, we accept the packet.
      Forward(options.acceptPort)
    )
  }
}

object SnatTarget extends BaseParsers {
  import ParserMP.monadPlusSyntax._

  def parser: Parser[Target] =
    for {
      _ <- iptParsers.jumpOptionParser

      // Parse the actual target.
      targetName <- someSpacesParser >> identifierParser if targetName == "SNAT"

      // Parse the mandatory '--to-source' target option.
      _ <- someSpacesParser >> parseString("--to-source")
      lowerIp <- someSpacesParser >> ipParser

      // Parse the optional upper bound ip.
      upperIp <- optional(parseChar('-') >> ipParser)

      // Parse the optional port range.
      maybePortRange <- optional(parseChar(':') >> portRangeParser)
    } yield SnatTarget(lowerIp, upperIp, maybePortRange)
}

object SnatTargetExtension extends TargetExtension {
  val targetParser = SnatTarget.parser
}
