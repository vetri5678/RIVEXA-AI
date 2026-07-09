function initShaderBackground(canvasId) {
  const canvas = document.getElementById(canvasId);
  if (!canvas) return null;

  // Sync the WebGL drawing-buffer size with the CSS-driven layout size.
  function syncSize() {
    const w = canvas.clientWidth  || 1280;
    const h = canvas.clientHeight || 720;
    if (canvas.width !== w || canvas.height !== h) {
      canvas.width  = w;
      canvas.height = h;
    }
  }
  if (typeof ResizeObserver !== 'undefined') {
    new ResizeObserver(syncSize).observe(canvas);
  }
  syncSize();

  const gl = canvas.getContext('webgl') || canvas.getContext('experimental-webgl');
  if (!gl) return null;
  
  const vs = `attribute vec2 a_position;
varying vec2 v_texCoord;
void main() {
  v_texCoord = a_position * 0.5 + 0.5;
  gl_Position = vec4(a_position, 0.0, 1.0);
}`;
  const fs = `precision highp float;
uniform float u_time;
uniform vec2 u_resolution;
varying vec2 v_texCoord;

void main() {
    vec2 uv = v_texCoord;
    float time = u_time * 0.2;
    
    // Create animated aurora-like noise
    float noise = sin(uv.x * 10.0 + time) * cos(uv.y * 8.0 - time * 0.5);
    noise += sin(uv.y * 12.0 + time * 0.8) * cos(uv.x * 6.0 + time);
    
    vec3 color1 = vec3(0.02, 0.03, 0.1); // Deep Navy
    vec3 color2 = vec3(0.0, 0.48, 1.0);   // Electric Blue
    vec3 color3 = vec3(0.44, 0.0, 1.0);   // Royal Purple
    
    vec3 finalColor = mix(color1, color2, noise * 0.5 + 0.5);
    finalColor = mix(finalColor, color3, sin(time + uv.x) * 0.3);
    
    gl_FragColor = vec4(finalColor * 0.6, 1.0);
}`;

  function compileShader(type, src) {
    const s = gl.createShader(type);
    gl.shaderSource(s, src);
    gl.compileShader(s);
    if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) {
      console.error('Shader compile error:', gl.getShaderInfoLog(s));
    }
    return s;
  }

  const prog = gl.createProgram();
  gl.attachShader(prog, compileShader(gl.VERTEX_SHADER, vs));
  gl.attachShader(prog, compileShader(gl.FRAGMENT_SHADER, fs));
  gl.linkProgram(prog);
  if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) {
    console.error('Program link error:', gl.getProgramInfoLog(prog));
    return null;
  }
  gl.useProgram(prog);

  const buf = gl.createBuffer();
  gl.bindBuffer(gl.ARRAY_BUFFER, buf);
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1,-1, 1,-1, -1,1, 1,1]), gl.STATIC_DRAW);
  
  const pos = gl.getAttribLocation(prog, 'a_position');
  gl.enableVertexAttribArray(pos);
  gl.vertexAttribPointer(pos, 2, gl.FLOAT, false, 0, 0);
  
  const uTime = gl.getUniformLocation(prog, 'u_time');
  const uRes = gl.getUniformLocation(prog, 'u_resolution');
  const uMouse = gl.getUniformLocation(prog, 'u_mouse');

  let mouse = { x: canvas.width / 2, y: canvas.height / 2 };
  
  function handleMouseMove(event) {
    const rect = canvas.getBoundingClientRect();
    if (rect.width && rect.height) {
      const nx = (event.clientX - rect.left) / rect.width;
      const ny = 1.0 - (event.clientY - rect.top) / rect.height;
      mouse.x = nx * canvas.width;
      mouse.y = ny * canvas.height;
    }
  }

  window.addEventListener('mousemove', handleMouseMove);

  let animationFrameId;

  function render(t) {
    if (typeof ResizeObserver === 'undefined') syncSize();
    gl.viewport(0, 0, canvas.width, canvas.height);
    if (uTime) gl.uniform1f(uTime, t * 0.001);
    if (uRes) gl.uniform2f(uRes, canvas.width, canvas.height);
    if (uMouse) gl.uniform2f(uMouse, mouse.x, mouse.y);
    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
    animationFrameId = requestAnimationFrame(render);
  }
  
  animationFrameId = requestAnimationFrame(render);

  return {
    destroy: () => {
      window.removeEventListener('mousemove', handleMouseMove);
      cancelAnimationFrame(animationFrameId);
    }
  };
}
