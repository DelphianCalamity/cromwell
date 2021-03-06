package cromwell.services.womtool

import cromwell.core.WorkflowSourceFilesCollection
import cromwell.services.ServiceRegistryActor.ServiceRegistryMessage

object WomtoolServiceMessages {

  final val WomtoolServiceName = "Womtool"

  sealed trait WomtoolServiceMessage extends ServiceRegistryMessage {
    override def serviceName: String = WomtoolServiceName
  }

  case class DescribeRequest(filesCollection: WorkflowSourceFilesCollection) extends WomtoolServiceMessage

  sealed trait DescribeResult extends WomtoolServiceMessage
  case class DescribeSuccess(description: WorkflowDescription) extends DescribeResult
  case class DescribeFailure(reason: String) extends DescribeResult

  case class WorkflowDescription(valid: Boolean, errors: List[String])
}
