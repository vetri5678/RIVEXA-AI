---
name: RiskVision AI
colors:
  surface: '#10131b'
  surface-dim: '#10131b'
  surface-bright: '#363942'
  surface-container-lowest: '#0b0e16'
  surface-container-low: '#181c23'
  surface-container: '#1c2028'
  surface-container-high: '#272a32'
  surface-container-highest: '#31353d'
  on-surface: '#e0e2ed'
  on-surface-variant: '#c1c6d7'
  inverse-surface: '#e0e2ed'
  inverse-on-surface: '#2d3039'
  outline: '#8b90a0'
  outline-variant: '#414755'
  surface-tint: '#adc6ff'
  primary: '#adc6ff'
  on-primary: '#002e69'
  primary-container: '#4b8eff'
  on-primary-container: '#00285c'
  inverse-primary: '#005bc1'
  secondary: '#d1bcff'
  on-secondary: '#3c0090'
  secondary-container: '#7000ff'
  on-secondary-container: '#ddcdff'
  tertiary: '#00dbe9'
  on-tertiary: '#00363a'
  tertiary-container: '#00a0aa'
  on-tertiary-container: '#002f33'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#d8e2ff'
  primary-fixed-dim: '#adc6ff'
  on-primary-fixed: '#001a41'
  on-primary-fixed-variant: '#004493'
  secondary-fixed: '#e9ddff'
  secondary-fixed-dim: '#d1bcff'
  on-secondary-fixed: '#23005b'
  on-secondary-fixed-variant: '#5700c9'
  tertiary-fixed: '#7df4ff'
  tertiary-fixed-dim: '#00dbe9'
  on-tertiary-fixed: '#002022'
  on-tertiary-fixed-variant: '#004f54'
  background: '#10131b'
  on-background: '#e0e2ed'
  surface-variant: '#31353d'
typography:
  display-xl:
    fontFamily: Geist
    fontSize: 72px
    fontWeight: '700'
    lineHeight: 80px
    letterSpacing: -0.04em
  display-lg:
    fontFamily: Geist
    fontSize: 48px
    fontWeight: '600'
    lineHeight: 56px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Geist
    fontSize: 32px
    fontWeight: '600'
    lineHeight: 40px
    letterSpacing: -0.01em
  headline-lg-mobile:
    fontFamily: Geist
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: -0.01em
  body-lg:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '400'
    lineHeight: 28px
    letterSpacing: 0em
  body-md:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: 0em
  label-mono:
    fontFamily: JetBrains Mono
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.05em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  unit: 8px
  container-max: 1440px
  gutter: 24px
  margin-desktop: 64px
  margin-tablet: 32px
  margin-mobile: 20px
---

## Brand & Style
The design system is engineered to evoke a sense of "Computational Intelligence"—a premium, high-fidelity environment where deep-learning insights meet cinematic clarity. It targets enterprise decision-makers and technical leads who require both a bird's-eye view of project health and granular risk diagnostics.

The style is a sophisticated evolution of **Glassmorphism**, characterized by hyper-refined frosted glass surfaces, multi-layered depth, and a "sub-surface" glow. The interface feels like a high-end command center, utilizing a deep space backdrop enriched by dynamic aurora gradients. Motion is fluid and intentional, mirroring the continuous processing of the AI engine.

## Colors
This design system utilizes a "Deep Nebula" palette. The core environment is set against a near-black foundation (#050816), allowing the vibrant neon accents to vibrate with maximum luminosity. 

- **Primary & Secondary:** Electric Blue and Royal Purple are used for high-level branding, active states, and primary actions.
- **Accents:** Neon Cyan is reserved for data visualizations and AI-driven highlights.
- **Semantic Logic:** Emerald Green (Healthy), Amber (At-Risk), and Coral Red (Critical) are utilized for status indicators, ensuring immediate cognitive recognition of project status.
- **Surface Treatment:** All UI containers use semi-transparent white fills at very low opacities (3-7%) to create the frosted glass effect without muddying the background gradients.

## Typography
The typographic hierarchy balances technical precision with editorial elegance. 

- **Display & Headlines:** Utilize **Geist** for its hyper-clean, technical architecture. Large headings should use tight letter-spacing and bold weights to command attention.
- **Body:** **Inter** provides maximum legibility for complex project data and risk descriptions.
- **Data & Labels:** **JetBrains Mono** is used for metrics, timestamps, and AI confidence scores to reinforce the "computed" nature of the data.

On mobile devices, display sizes are scaled down aggressively to maintain readability while preserving the same weight characteristics.

## Layout & Spacing
The layout follows a 12-column fluid grid system with generous outer margins to create an "airy," premium feel. 

- **Breathing Room:** Elements are spaced using an 8px base grid, emphasizing white space to prevent the dense AI data from feeling overwhelming.
- **Layered Layout:** Content is organized into floating "Island" modules rather than a standard flat page layout.
- **Responsive Behavior:** On desktop, use wide margins (64px) to center the content. On mobile, margins shrink to 20px, and grid columns collapse into a single-column stack, prioritizing the most critical risk metrics at the top of the scroll.

## Elevation & Depth
Depth is the primary communicator of hierarchy in this design system. It is achieved through three core techniques:

1.  **Backdrop Blurs:** All elevated surfaces must use a `backdrop-filter: blur(20px)`. This creates the frosted glass effect, ensuring text remains readable over the background gradients.
2.  **Dual-Stroke Borders:** Glass cards feature a 1px top-left border of `rgba(255,255,255,0.15)` and a bottom-right border of `rgba(255,255,255,0.05)`. This mimics light hitting a physical glass edge.
3.  **Subtle Glows:** Active or high-risk elements use a soft outer glow (drop-shadow) tinted with the primary color (e.g., `box-shadow: 0 0 30px rgba(0, 122, 255, 0.15)`).
4.  **Z-Axis Hierarchy:** High-priority modals and dropdowns have a higher blur (40px) and a slightly more opaque background to appear "closer" to the user.

## Shapes
The shape language is sophisticated and modern, avoiding both sharp aggressive corners and overly playful circles. 

- **Primary Cards:** Use a specific 22px corner radius (`rounded-xl` / `1.5rem` equivalent) to create a premium, hardware-inspired look reminiscent of modern OS design.
- **Small Elements:** Buttons and input fields use a 12px radius to maintain consistency with the larger containers.
- **Interactive States:** Hovering over elements should trigger a subtle expansion in size (102% scale) alongside a glow transition.

## Components
- **Floating Glass Navigation:** A horizontal bar docked at the top or bottom with a heavy backdrop blur. Icons should be minimal line-art with a "neon" active state.
- **Premium Risk Cards:** These are the primary data containers. They feature 22px rounded corners, a subtle 1px inner border, and a "hotspot" glow in the corner corresponding to the risk level (Blue for low, Red for critical).
- **Animated Borders:** For "AI Analyzing" states, use a CSS-conic-gradient animation that travels along the 1px border of a card.
- **Buttons:** 
    - *Primary:* Solid Electric Blue with a subtle inner highlight.
    - *Secondary:* Ghost style with a frosted glass background and white text.
- **Risk Gauges:** Circular progress indicators using "Neon Cyan" to "Coral Red" gradients, utilizing thick stroke widths and rounded caps.
- **Input Fields:** Dark, recessed backgrounds with 1px glass borders that glow Electric Blue upon focus.