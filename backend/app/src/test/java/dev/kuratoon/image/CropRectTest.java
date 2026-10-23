package dev.kuratoon.image;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kuratoon.media.CoverCrop;
import org.junit.jupiter.api.Test;

class CropRectTest {

    @Test
    void noCropIsTheLargestCenteredTwoByThree() {
        // Landscape: full height, centered horizontally.
        assertThat(CropRect.forAspect(1000, 600, null, 2, 3)).isEqualTo(new CropRect(300, 0, 400, 600));
        // Tall strip: full width, centered vertically.
        assertThat(CropRect.forAspect(800, 12000, null, 2, 3)).isEqualTo(new CropRect(0, 5400, 800, 1200));
        // Already 2:3.
        assertThat(CropRect.forAspect(800, 1200, null, 2, 3)).isEqualTo(new CropRect(0, 0, 800, 1200));
    }

    @Test
    void cropIsScaledAndSnappedToTheAspectAroundItsCenter() {
        // A 2:3 crop of the left half of a 400x900 image.
        assertThat(CropRect.forAspect(400, 900, new CoverCrop(0, 0, 0.5, 1), 2, 3))
                .isEqualTo(new CropRect(0, 300, 200, 300));
        // Exact 2:3 crop stays as is.
        assertThat(CropRect.forAspect(1000, 1500, new CoverCrop(0.1, 0.2, 0.4, 0.4), 2, 3))
                .isEqualTo(new CropRect(100, 300, 400, 600));
    }

    @Test
    void outOfRangeCropsStayInsideTheImage() {
        CropRect rect = CropRect.forAspect(400, 600, new CoverCrop(0.9, 0.9, 0.5, 0.5), 2, 3);
        assertThat(rect.left() + rect.width()).isLessThanOrEqualTo(400);
        assertThat(rect.top() + rect.height()).isLessThanOrEqualTo(600);
        assertThat(rect.width()).isPositive();
        // A degenerate crop falls back to the centered default.
        assertThat(CropRect.forAspect(400, 600, new CoverCrop(1, 1, 0, 0), 2, 3))
                .isEqualTo(new CropRect(0, 0, 400, 600));
    }
}
