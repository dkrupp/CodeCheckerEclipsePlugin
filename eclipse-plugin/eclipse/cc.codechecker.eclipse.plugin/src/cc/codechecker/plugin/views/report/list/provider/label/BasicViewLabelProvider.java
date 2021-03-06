package cc.codechecker.plugin.views.report.list.provider.label;

import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

import cc.codechecker.api.action.result.ReportInfo;
import cc.codechecker.api.job.report.list.SearchList;
import cc.codechecker.plugin.views.report.list.ReportListView;

public class BasicViewLabelProvider extends LabelProvider {

    private final ReportListView reportListView;

    public BasicViewLabelProvider(ReportListView reportListView) {
        this.reportListView = reportListView;
    }

    public String getText(Object obj) {
        if (obj instanceof ReportInfo) {
            ReportInfo ri = (ReportInfo) obj;

            return "#" + ri.getReportId() + ": " + ri.getCheckedFile() + " [" + ri
                    .getLastBugPathItem().getStartPosition().getLine() + ":" + ri
                    .getLastBugPathItem().getStartPosition().getColumn() + "]";
        }
        if (obj instanceof String) {
            // TODO: add counters
            //return ((String)obj) + " (?)";
        }
        return obj.toString();
    }

    public Image getImage(Object obj) {
        String imageKey = ISharedImages.IMG_OBJ_ELEMENT;
        if (obj instanceof String || obj instanceof SearchList) // TODO: provide better images
            imageKey = ISharedImages.IMG_OBJ_FOLDER;
        return PlatformUI.getWorkbench().getSharedImages().getImage(imageKey);
    }
}
