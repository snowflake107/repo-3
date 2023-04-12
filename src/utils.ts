export const sleep = (sec = 1) => new Promise((resolve) => setTimeout(resolve, sec * 1000));
