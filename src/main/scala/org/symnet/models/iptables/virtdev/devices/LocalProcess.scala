// Copyright (C) 2017 Calin Cruceru <calin.cruceru@stud.acs.upb.ro>.
//
// See the LICENCE file distributed with this work for additional
// information regarding copyright ownership.

package org.symnet
package models.iptables.virtdev
package devices

/** A local process acts as a sink. It has one input port and no output ports. */
case class LocalProcess(name: String)
  extends RegularVirtualDevice[Unit](name, 1, 0, ()) {

  def inputPort: Port = inputPort(0)

  override def portInstructions: Map[Port, Instruction] = Map.empty
}
