import { component$ } from '@qwik.dev/core';

interface LarpDotLogoProps {
  size?: number;
  class?: string;
}

export const LarpDotLogo = component$<LarpDotLogoProps>(
  ({ size = 28, class: className }) => {
    return (
      <svg
        width={size}
        height={size}
        viewBox="0 0 24 24"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        class={className}
        aria-hidden="true"
      >
        <path
          fill="currentColor"
          fill-rule="evenodd"
          clip-rule="evenodd"
          d="M7 8h10c2.21 0 4 1.79 4 4s-1.79 4-4 4H7c-2.21 0-4-1.79-4-4s1.79-4 4-4Zm5 2a2 2 0 1 0 0 4 2 2 0 1 0 0-4Z"
        />
      </svg>
    );
  }
);
