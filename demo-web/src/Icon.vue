<script setup>
defineProps({ name: String, size: { type: [Number, String], default: 20 } });
const paths = {
  plus: "M12 5v14M5 12h14",
  chevron: "m9 5 7 7-7 7",
  arrow: "M4 12h16m-6-6 6 6-6 6",
  search: "m21 21-5-5M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0",
  check: "m5 12 4 4L19 6",
  people:
    "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M16 3a4 4 0 0 1 0 8M22 21v-2a4 4 0 0 0-3-3.9M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0",
  heart:
    "M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8",
  activity: "M2 12h5l3-8 4 16 3-8h5",
  moon: "M21 13a9 9 0 1 1-10-10 7 7 0 0 0 10 10",
  leaf: "M20 3c-8 0-15 2-15 10a6 6 0 0 0 6 6c8 0 9-8 9-16ZM4 21 15 10",
  pill: "m10 4-6 6a6 6 0 0 0 8 8l6-6a6 6 0 0 0-8-8Zm-3 3 10 10",
  flower:
    "M12 9C5 0 1 10 9 12 0 19 10 23 12 15c7 9 11-1 3-3 9-7-1-11-3-3ZM12 10v4m-2-2h4",
  grid: "M3 3h7v7H3V3Zm11 0h7v7h-7V3ZM3 14h7v7H3v-7Zm11 0h7v7h-7v-7Z",
  target: "M20 12a8 8 0 1 1-8-8M12 12l9-9M16 3h5v5M15 12a3 3 0 1 1-3-3",
  file: "M14 2H5v20h14V7l-5-5Zm0 0v5h5M8 12h8m-8 4h5",
  spark: "m12 2 2.6 7.4L22 12l-7.4 2.6L12 22l-2.6-7.4L2 12l7.4-2.6L12 2Z",
  clock: "M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0ZM12 7v5l3 2",
  refresh: "M20 7v5h-5M4 17v-5h5M6 6a8 8 0 0 1 13 3M18 18A8 8 0 0 1 5 15",
  link: "M10 13a5 5 0 0 0 7 0l3-3a5 5 0 0 0-7-7l-2 2M14 11a5 5 0 0 0-7 0l-3 3a5 5 0 0 0 7 7l2-2",
  info: "M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0ZM12 11v6m0-10v.1",
  save: "M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h12l4 4v12a2 2 0 0 1-2 2ZM7 3v6h10V3M7 21v-8h10v8",
  close: "m6 6 12 12M6 18 18 6",
  calendar: "M4 5h16v16H4V5ZM8 2v6m8-6v6M4 11h16",
};
</script>
<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.65"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path :d="paths[name] || paths.file" />
  </svg>
</template>
