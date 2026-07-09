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
    const startValue = current;
    const diff = value - startValue;

    if (diff === 0) return;

    const step = (timestamp: number) => {
      if (!startTimestamp) startTimestamp = timestamp;
      const progress = Math.min((timestamp - startTimestamp) / duration, 1);
      setCurrent(startValue + diff * progress);

      if (progress < 1) {
        window.requestAnimationFrame(step);
      }
    };

    window.requestAnimationFrame(step);
  }, [value]);

  return <>{formatter(current)}</>;
};
export default AnimatedNumber;
