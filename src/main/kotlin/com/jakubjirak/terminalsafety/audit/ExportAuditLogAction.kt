package com.jakubjirak.terminalsafety.audit

import com.intellij.ide.actions.RevealFileAction
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWrapper
import java.nio.file.Files

/**
 * Headless CSV/JSON exporter for the Incident Audit Log, intended for CI
 * pipelines and compliance tooling.
 *
 * The action picks CSV vs JSON based on the extension the user enters in
 * the save dialog (`.csv` → CSV, anything else → JSON). Runs entirely on a
 * background task and emits a notification with the resulting row count
 * plus a "Reveal in Finder/Explorer" action.
 */
class ExportAuditLogAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val descriptor = FileSaverDescriptor(
            "Export Audit Log",
            "Save the full audit log; extension (.csv or .json) selects the format.",
            "csv", "json",
        )
        val wrapper: VirtualFileWrapper = FileChooserFactory.getInstance()
            .createSaveFileDialog(descriptor, project)
            .save(null as VirtualFile?, "audit-${System.currentTimeMillis()}.csv") ?: return
        val target = wrapper.file.toPath()
        val asJson = target.fileName.toString().lowercase().endsWith(".json")

        object : Task.Backgroundable(project, "Exporting audit log", false) {
            override fun run(indicator: ProgressIndicator) {
                val events = AuditLogService.getInstance(project).store()
                    .query(AuditFilter())
                    .toList()
                val text = if (asJson) AuditExporter.toJson(events.asSequence())
                else AuditExporter.toCsv(events.asSequence())
                Files.writeString(target, text)
                notifySuccess(project, events.size, target.toFile())
            }
        }.queue()
    }

    private fun notifySuccess(project: Project, rows: Int, file: java.io.File) {
        val group = NotificationGroupManager.getInstance()
            .getNotificationGroup("Terminal Safety Rails")
        val n = group.createNotification(
            "Audit log exported",
            "Wrote $rows audit event(s) to ${file.name}",
            NotificationType.INFORMATION,
        )
        n.addAction(NotificationAction.createSimple("Reveal in Files") {
            RevealFileAction.openFile(file)
        })
        n.notify(project)
    }
}
