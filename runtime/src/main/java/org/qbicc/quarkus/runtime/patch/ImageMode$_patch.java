package org.qbicc.quarkus.runtime.patch;

import io.quarkus.runtime.ImageMode;
import org.qbicc.runtime.Build;
import org.qbicc.runtime.patcher.PatchClass;
import org.qbicc.runtime.patcher.Replace;

@PatchClass(ImageMode.class)
final class ImageMode$_patch {
    @Replace
    static ImageMode current() {
        if (Build.isHost()) {
            return ImageMode.NATIVE_BUILD;
        } else if (Build.isTarget()) {
            return ImageMode.NATIVE_RUN;
        } else {
            return ImageMode.JVM;
        }
    }
}
