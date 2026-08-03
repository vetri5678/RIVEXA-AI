import React, { useEffect, useState } from 'react';

interface AnimatedNumberProps {
  value: number;
  duration?: number;
  formatter?: (val: number) => string;
}

export const AnimatedNumber: React.FC<AnimatedNumberProps> = ({
  value,
  duration = 800,
  formatter = (val) => String(Math.round(val)),
}) => {
  const [current, setCurrent] = useState(0);

  useEffect(() => {
    let startTimestamp: number | null = null;
    let animFrameId: number;
    const startValue = current;
    const diff = value - startValue;

    if (diff === 0) return;

    const step = (timestamp: number) => {
      if (!startTimestamp) startTimestamp = timestamp;
      const progress = Math.min((timestamp - startTimestamp) / duration, 1);
      setCurrent(startValue + diff * progress);

      if (progress < 1) {
        animFrameId = window.requestAnimationFrame(step);
      }
    };

    animFrameId = window.requestAnimationFrame(step);
    return () => window.cancelAnimationFrame(animFrameId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value, duration]);

  return <>{formatter(current)}</>;
};
export default AnimatedNumber;
