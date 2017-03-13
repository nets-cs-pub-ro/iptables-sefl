// Copyright (C) 2017 Calin Cruceru <calin.cruceru@stud.acs.upb.ro>.
//
// See the LICENCE file distributed with this work for additional
// information regarding copyright ownership.

package org.symnet.models.iptables.core

abstract class Target(name: String) {
  def isValid(rule: Rule, chain: Chain, table: Table): Boolean
}

/** PLaceholder target is used when a (possible) forward reference to a user
 *  defined chain is made.
 *
 *  The replacement in the resulting parse tree is done at a later stage
 *  (following the complete parsing).
 */
case class PlaceholderTarget(
    name: String,
    goto: Boolean = false) extends Target(name) {

  /** We shouldn't get to check the validty of a placeholder target.
   *
   *  TODO(calincru): It should be replaced on a prior semantic validation step.
   */
  override def isValid(rule: Rule, chain: Chain, table: Table): Boolean = false
}
