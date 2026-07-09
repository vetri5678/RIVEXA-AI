function initThreeBrain(containerId) {
  const container = document.getElementById(containerId);
  if (!container) return null;

  // Clear container in case it has existing canvases
  container.innerHTML = '';

  const devicePixelRatio = window.devicePixelRatio || 1;
  let width = container.clientWidth || window.innerWidth;
  let height = container.clientHeight || window.innerHeight;

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(75, width / height, 0.1, 1000);
  const renderer = new THREE.WebGLRenderer({ alpha: true, antialias: true });
  renderer.setSize(width, height);
  renderer.setPixelRatio(devicePixelRatio);
  container.appendChild(renderer.domElement);

  // Create a futuristic "Brain/Core" using a group of geometries
  const coreGroup = new THREE.Group();

  const geometry = new THREE.IcosahedronGeometry(2, 1);
  const material = new THREE.MeshPhongMaterial({
      color: 0x007AFF,
      wireframe: true,
      transparent: true,
      opacity: 0.8
  });
  const core = new THREE.Mesh(geometry, material);
  coreGroup.add(core);

  // Outer shell
  const shellGeo = new THREE.IcosahedronGeometry(3, 0);
  const shellMat = new THREE.MeshBasicMaterial({
      color: 0x7000FF,
      wireframe: true,
      transparent: true,
      opacity: 0.2
  });
  const shell = new THREE.Mesh(shellGeo, shellMat);
  coreGroup.add(shell);

  scene.add(coreGroup);

  const ambientLight = new THREE.AmbientLight(0xffffff, 0.5);
  scene.add(ambientLight);

  const pointLight = new THREE.PointLight(0x00F0FF, 1);
  pointLight.position.set(5, 5, 5);
  scene.add(pointLight);

  camera.position.z = 8;

  let animationFrameId;

  function animate() {
      animationFrameId = requestAnimationFrame(animate);
      coreGroup.rotation.y += 0.005;
      coreGroup.rotation.x += 0.003;
      renderer.render(scene, camera);
  }

  function handleResize() {
      const w = container.clientWidth || window.innerWidth;
      const h = container.clientHeight || window.innerHeight;
      renderer.setSize(w, h);
      camera.aspect = w / h;
      camera.updateProjectionMatrix();
  }

  window.addEventListener('resize', handleResize);
  animate();

  return {
    resize: handleResize,
    destroy: () => {
      window.removeEventListener('resize', handleResize);
      cancelAnimationFrame(animationFrameId);
      if (renderer) {
        renderer.dispose();
      }
      container.innerHTML = '';
    }
  };
}
