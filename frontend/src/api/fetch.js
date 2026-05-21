export const fetchIdentityGraph = async () => {
  const response = await fetch('/api/identity/graph');
  return response.json();
};
