package dev.frostguard.app;

import dev.frostguard.api.runtime.RuntimeChannel;
import dev.frostguard.api.runtime.WorkspacePaths;

public final class ApplicationTitle {
    private ApplicationTitle() {
    }

    public static String current() {
        String productName = System.getProperty("frostguard.brand.name", "").trim();
        if (productName.isEmpty()) {
            productName = WorkspacePaths.current().channel().productName();
        }
        return format(WorkspacePaths.current().channel(), RuntimeVersion.current(),
                RuntimeInstanceIdentity.current(), productName);
    }

    static String format(RuntimeChannel channel, String version, String instanceLabel) {
        return format(channel, version, instanceLabel, channel.productName());
    }

    static String format(RuntimeChannel channel, String version, String instanceLabel, String productName) {
        String title = String.format("%s · %s", productName, instanceLabel);
        return version == null || version.isBlank() ? title : title + " · v" + version;
    }
}
