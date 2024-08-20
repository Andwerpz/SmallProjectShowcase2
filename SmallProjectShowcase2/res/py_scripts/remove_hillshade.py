from PIL import Image
import numpy as np
import matplotlib.pyplot as plt

# Load the images
elevation_map_path = "elevation_hillshade.png"
hillshade_texture_path = "hillshade.png"

elevation_map = Image.open(elevation_map_path).convert("L")
hillshade_texture = Image.open(hillshade_texture_path).convert("L")

# Convert images to numpy arrays
elevation_map_np = np.array(elevation_map).astype(np.float32)
hillshade_texture_np = np.array(hillshade_texture).astype(np.float32)

# Normalize the images to [0, 1] range
elevation_map_normalized = elevation_map_np / 255.0
hillshade_texture_normalized = hillshade_texture_np / 255.0
# elevation_map_normalized = elevation_map_np
# hillshade_texture_normalized = hillshade_texture_np

# Perform the division with the corrected order: Hillshade Texture divided by Elevation Map
# Adding a small epsilon value to avoid division by zero
epsilon = 1e-10
result_image_normalized_corrected = hillshade_texture_normalized / (elevation_map_normalized + epsilon)

# Rescale the result back to [0, 255]
result_image_corrected = (result_image_normalized_corrected * 255).astype(np.uint8)
# result_image_corrected = result_image_normalized_corrected/

# Convert the result to an image
result_image_pil_corrected = Image.fromarray(result_image_corrected)

# Display the corrected processed image
plt.figure(figsize=(10, 5))
plt.title("Corrected Result (Hillshading Removed)")
# plt.imshow(result_image_pil_corrected, cmap="gray", vmin = 0, vmax = 255)
plt.imshow(hillshade_texture, cmap = "gray", vmin = 0, vmax = 255)
plt.axis("off")
plt.show()

# Save the corrected resulting image
output_path_corrected = "/mnt/data/result_image_corrected.png"
result_image_pil_corrected.save(output_path_corrected)